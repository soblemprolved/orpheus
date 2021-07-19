package service.converters

import model.*
import okhttp3.Response
import org.jsoup.Jsoup
import java.time.LocalDate

object WorkConverter : Converter<WorkConverter.WorkResult> {
    data class WorkResult(val work: Work, val csrfToken: String)

    override fun convert(response: Response): WorkResult {
        val workUrl = response.request.url
        val id = workUrl.encodedPathSegments.last().toLong()

        val body = response.body!!.string()
        // check if response body is already closed
        val doc = Jsoup.parse(body)

        val metadataTree = doc.select("dl.work.meta.group")
        val statisticsTree = metadataTree.select("dl.stats")
        val chapterCountArray = statisticsTree.select("dd.chapters")
            .first()
            .text()
            .split("/")

        val rating = metadataTree.select("dd.rating.tags")
            .select("a.tag")
            .first()
            .text()
            .let { Rating.fromName(it) }
        val warnings = metadataTree.select("dd.warning.tags")
            .select("a.tag")
            .map { Warning.fromName(it.text()) }
        val categories = metadataTree.select("dd.category.tags")
            .select("a.tag")
            .map { Category.fromName(it.text()) }
        val fandoms = metadataTree.select("dd.fandom.tags")
            .select("a.tag")
            .map { it.text() }
        val relationships = metadataTree.select("dd.relationship.tags")
            .select("a.tag")
            .map { it.text() }
        val characters = metadataTree.select("dd.character.tags")
            .select("a.tag")
            .map { it.text() }
        val freeforms = metadataTree.select("dd.freeform.tags")
            .select("a.tag")
            .map { it.text() }
        val language = metadataTree.select("dd.language")
            .first()
            .text()
            .let { Language.fromName(it) }

        val publishDate = statisticsTree.select("dd.published")
            .first()
            .text()
            .let { LocalDate.parse(it) }

        val lastUpdatedDate = statisticsTree.select("dd.status")
            .first()
            ?.text()
            ?.let { LocalDate.parse(it) }
            ?: publishDate
        val wordCount = statisticsTree.select("dd.words")
            .first()
            .text()
            .toInt()
        val currentChapterCount = chapterCountArray[0].toInt()
        val maxChapterCount = chapterCountArray[1].toIntOrNull() ?: 0
        val completionStatus = currentChapterCount == maxChapterCount
        val comments = statisticsTree.select("dd.comments")
            .first()
            ?.text()
            ?.toInt()
            ?: 0
        val kudos = statisticsTree.select("dd.kudos")
            .first()
            ?.text()
            ?.toInt()
            ?: 0
        val bookmarks = statisticsTree.select("dd.bookmarks")
            .first()
            ?.text()
            ?.toInt()
            ?: 0
        val hits = statisticsTree.select("dd.hits")
            .first()
            .text()
            .toInt()

        val title = doc
            .select("div#workskin > div.preface.group > h2.title.heading")
            .first()
            .text()

        val summary = Html(
            doc.selectFirst("div#workskin > div.preface.group > div.summary.module > blockquote.userstuff")
            .html()
        )

        val authors = doc
            .select("div#workskin > div.preface.group > h3.byline.heading > a[href]")
            .let {
                if (it.isEmpty()) {
                    doc.select("div#workskin > div.preface.group > h3.byline.heading")
                        .map{ element -> User.from(element.text(), hasUrl = false) }
                } else {
                    it.map { element -> User.from(element.text(), hasUrl = true) }
                }
            }


        val giftees = doc
            .select("#workskin > div.preface.group > div.notes.module > ul.associations > li > a")
            .map { User.from(it.text(), hasUrl = true) }  // pseuds can be parsed based on names alone

        val preWorkNotes = Html(
            doc.select("#workskin > div.preface.group > div.notes.module > blockquote.userstuff")
            .first()
            ?.html()
            ?: ""
        )

        val postWorkNotes = Html(
            doc.select("div#work_endnotes > blockquote.userstuff")
                .first()
                ?.html()
                ?: ""
        )

        val workskin = doc.select("div#main > style[type='text/css']")
            .first()
            ?.html()
            ?.let { Css(it) }
            ?: Css("")

        // Because Entire Work doesn't actually work on completed oneshots, we need to change chapterTrees
        val work = if (currentChapterCount == 1 && maxChapterCount == 1) {

            // TODO: return a single chapter work
            // get the list of URLs to zip with the contents later

            val chapterText = doc.select("div#chapters > div.userstuff")
                .first()
                .html()
                .let { Html(it) }

            SingleChapterWork(
                id = id,
                title = title,
                authors = authors,
                giftees = giftees,
                publishedDate = publishDate,
                lastUpdatedDate = lastUpdatedDate,
                fandoms = fandoms,
                rating = rating,
                warnings = warnings,
                categories = categories,
                characters = characters,
                relationships = relationships,
                freeforms = freeforms,
                summary = summary,
                language = language,
                wordCount = wordCount,
                chapterCount = currentChapterCount,
                maxChapterCount = maxChapterCount,
                commentCount = comments,
                kudosCount = kudos,
                bookmarkCount = bookmarks,
                hitCount = hits,
                preWorkNotes = preWorkNotes,
                body = chapterText,
                postWorkNotes = postWorkNotes,
                workskin = workskin
            )

        } else {
            // TODO: Execute this branch if the work is not a *completed* oneshot.
            // i.e. this is a multi-work series or incomplete single chapter work etc
            // Get chapter trees
            val chapterTrees = doc.select("div#chapters > div.chapter")

            // assigns the result of the map to chapters
            val chapters = chapterTrees.map {
                // TODO: parse the url inside here
                val chapterTitle = it
                    .select("div.chapter.preface.group > h3.title")
                    .first()
                    .ownText()  // gets the text enclosed within the element that is *not* nested in other elements
                    .removePrefix(": ")

                // chapter url obtained here is the same as the one in the navigation list
                val chapterId = it
                    .selectFirst("div.chapter.preface.group > h3.title > a[href]")
                    .attr("href")
                    .removePrefix("/works/$id/chapters/")
                    .toLong()

                val chapterSummary = it
                    .select("div#summary > blockquote.userstuff")
                    .first()
                    ?.html()
                    ?.let { Html(it) }
                    ?: Html("")
                val preChapterNotes = it
                    .select("div#notes > blockquote.userstuff")
                    .first()
                    ?.html()
                    ?.let { Html(it) }
                    ?: Html("")
                val chapterText = it
                    .select("div.userstuff.module")
                    .first()
                    .apply { this.getElementById("work")?.remove() }
                    .html()
                    .let { Html(it) }
                val postChapterNotes = it
                    .select("div.chapter.preface.group > div.end.notes.module > blockquote.userstuff")
                    .first()
                    ?.html()
                    ?.let { Html(it) }
                    ?: Html("")

                return@map Chapter(
                    id = chapterId,
                    title = title,
                    summary = summary,
                    preChapterNotes = preChapterNotes,
                    body = chapterText,
                    postChapterNotes = postChapterNotes,
                )
            }

            MultiChapterOrIncompleteWork(
                id = id,
                title = title,
                authors = authors,
                giftees = giftees,
                publishedDate = publishDate,
                lastUpdatedDate = lastUpdatedDate,
                fandoms = fandoms,
                rating = rating,
                warnings = warnings,
                categories = categories,
                characters = characters,
                relationships = relationships,
                freeforms = freeforms,
                summary = summary,
                language = language,
                wordCount = wordCount,
                chapterCount = currentChapterCount,
                maxChapterCount = maxChapterCount,
                commentCount = comments,
                kudosCount = kudos,
                bookmarkCount = bookmarks,
                hitCount = hits,
                preWorkNotes = preWorkNotes,
                chapters = chapters,
                postWorkNotes = postWorkNotes,
                workskin = workskin
            )
        }

        val csrfToken = Converter.getCsrfFromJsoupDoc(doc)

        return WorkResult(work, csrfToken)
    }
}
