package com.github.bzumhagen.sct.git

import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.Calendar

import better.files.File
import com.github.bzumhagen.sct.{ChangelogChange, ChangelogConfiguration}
import com.github.zafarkhaja.semver.Version
import org.eclipse.jgit.api.Git
import org.scalatest.{FlatSpec, Matchers}

class GitChangelogTest extends FlatSpec with Matchers {
  private val DefaultConfiguration = ChangelogConfiguration.load()
  private val DefaultDescription = "My Default Change"
  private val Today = LocalDateTime.now().toLocalDate

  "GitChangelog" should "get changes in git repository" in {
    val (dir, repo) = initializeGitRepo
    val gitChangelog = new GitChangelog(DefaultConfiguration, dir)
    val expectedChange = ChangelogChange(DefaultDescription, Version.valueOf("1.0.0"), "added", Some("XYZ-123"), Today)

    commitToRepo(repo, expectedChange.description, expectedChange.version, expectedChange.changeType, expectedChange.reference.get)

    gitChangelog.getChanges shouldBe Seq(expectedChange)
  }

  "GitChangelog" should "skip changes without version or tag" in {
    val (dir, repo) = initializeGitRepo
    val gitChangelog = new GitChangelog(DefaultConfiguration, dir)
    val expectedChange = ChangelogChange(DefaultDescription, Version.valueOf("1.0.0"), "added", Some("XYZ-123"), Today)

    commitToRepoWithoutTag(repo, "Some change without a tag", Version.valueOf("1.0.0"), "")
    commitToRepoWithoutVersion(repo, "Some change without a version", "added", "")
    commitToRepo(repo, expectedChange.description, expectedChange.version, expectedChange.changeType, expectedChange.reference.get)
    commitToRepoWithOnlyDescription(repo, "Some change with only a description")

    gitChangelog.getChanges shouldBe Seq(expectedChange)
  }

  it should "generate a smartGrouped markdown file properly" in {
    val (dir, repo) = initializeGitRepo
    val gitChangelog = new GitChangelog(DefaultConfiguration.copy(smartGrouping = true), dir)
    val changes = Seq(
      ChangelogChange("Create project", Version.valueOf("1.0.0"), "Added", Some("XYZ-123"), Today),
      ChangelogChange("Add some functionality", Version.valueOf("1.1.0"), "Added", Some("XYZ-124"), Today),
      ChangelogChange("Deprecate some functionality", Version.valueOf("1.1.1"), "Deprecated", Some("XYZ-125"), Today),
      ChangelogChange("Remove some deprecated functionality", Version.valueOf("2.0.0"), "Removed", Some("XYZ-126"), Today),
      ChangelogChange("Change some behavior", Version.valueOf("2.0.1"), "Changed", Some("XYZ-127"), Today),
      ChangelogChange("Change some behavior again", Version.valueOf("2.0.2"), "Changed", Some("XYZ-128"), Today),
      ChangelogChange("Change some behavior a third time", Version.valueOf("2.0.3"), "Changed", Some("XYZ-129"), Today),
      ChangelogChange("Add some more functionality", Version.valueOf("2.1.0"), "Added", Some("XYZ-130"), Today),
      ChangelogChange("Change some behavior yet again", Version.valueOf("2.1.1"), "Changed", Some("XYZ-131"), Today)
    )
    val now = Calendar.getInstance().getTime
    val standardDateFormat = new SimpleDateFormat("yyyy-MM-dd")
    val currentDate = standardDateFormat.format(now)
    val changelogFile = File.newTemporaryFile("changelogTestFile")
    val expectedMarkdown =
      s"""# SCT Change Log
        |All notable changes to this project will be documented in this file.
        |
        |The format is based on [Keep a Changelog](http://keepachangelog.com/)
        |and this project adheres to [Semantic Versioning](http://semver.org/).
        |
        |## [2.1.1] - $currentDate
        |### Changed
        |- Change some behavior yet again
        |
        |## [2.1.0] - $currentDate
        |### Added
        |- Add some more functionality
        |### Changed
        |- Change some behavior a third time
        |- Change some behavior again
        |- Change some behavior
        |
        |## [2.0.0] - $currentDate
        |### Added
        |- Add some functionality
        |### Removed
        |- Remove some deprecated functionality
        |### Deprecated
        |- Deprecate some functionality
        |
        |## [1.0.0] - $currentDate
        |### Added
        |- Create project
        |
        |""".stripMargin

    changes.foreach(change => commitToRepo(repo, change.description, change.version, change.changeType, change.reference.get))

    val actualChanges = gitChangelog.getChanges
    gitChangelog.generateMarkdown(changelogFile, actualChanges).contentAsString shouldBe expectedMarkdown
  }

  it should "generate a verbose markdown file properly" in {
    val (dir, repo) = initializeGitRepo
    val gitChangelog = new GitChangelog(DefaultConfiguration.copy(smartGrouping = false), dir)
    val changes = Seq(
      ChangelogChange("Create project", Version.valueOf("1.0.0"), "Added", Some("XYZ-123"), Today),
      ChangelogChange("Add some functionality", Version.valueOf("1.1.0"), "Added", Some("XYZ-124"), Today),
      ChangelogChange("Deprecate some functionality", Version.valueOf("1.1.1"), "Deprecated", Some("XYZ-125"), Today),
      ChangelogChange("Remove some deprecated functionality", Version.valueOf("2.0.0"), "Removed", Some("XYZ-126"), Today),
      ChangelogChange("Change some behavior", Version.valueOf("2.0.1"), "Changed", Some("XYZ-127"), Today)
    )
    val now = Calendar.getInstance().getTime
    val standardDateFormat = new SimpleDateFormat("yyyy-MM-dd")
    val currentDate = standardDateFormat.format(now)
    val changelogFile = File.newTemporaryFile("changelogTestFile")
    val expectedMarkdown =
      s"""# SCT Change Log
        |All notable changes to this project will be documented in this file.
        |
        |The format is based on [Keep a Changelog](http://keepachangelog.com/)
        |and this project adheres to [Semantic Versioning](http://semver.org/).
        |
        |## [2.0.1] - $currentDate
        |### Changed
        |- Change some behavior
        |
        |## [2.0.0] - $currentDate
        |### Removed
        |- Remove some deprecated functionality
        |
        |## [1.1.1] - $currentDate
        |### Deprecated
        |- Deprecate some functionality
        |
        |## [1.1.0] - $currentDate
        |### Added
        |- Add some functionality
        |
        |## [1.0.0] - $currentDate
        |### Added
        |- Create project
        |
        |""".stripMargin

    changes.foreach(change => commitToRepo(repo, change.description, change.version, change.changeType, change.reference.get))

    val actualChanges = gitChangelog.getChanges
    gitChangelog.generateMarkdown(changelogFile, actualChanges).contentAsString shouldBe expectedMarkdown
  }

  private def initializeGitRepo: (File, Git) = {
    val gitDir = File.newTemporaryDirectory("changelogTestRepo")
    gitDir -> Git.init.setDirectory(gitDir.toJava).call
  }

  private def commitToRepo(repo: Git, description: String, version: Version, tag: String, reference: String): Unit = {
    repo.commit.setAllowEmpty(true).setMessage(
      s"$description\n\nThis is the long form description of my change\n\nversion: $version\ntag: $tag\nresolves: $reference"
    ).call()
  }

  private def commitToRepoWithoutVersion(repo: Git, description: String, tag: String, reference: String): Unit = {
    repo.commit.setAllowEmpty(true).setMessage(
      s"$description\n\nThis is the long form description of my change\n\ntag: $tag\nresolves: $reference"
    ).call()
  }

  private def commitToRepoWithoutTag(repo: Git, description: String, version: Version, reference: String): Unit = {
    repo.commit.setAllowEmpty(true).setMessage(
      s"$description\n\nThis is the long form description of my change\n\nversion: $version\nresolves: $reference"
    ).call()
  }

  private def commitToRepoWithOnlyDescription(repo: Git, description: String): Unit = {
    repo.commit.setAllowEmpty(true).setMessage(
      s"$description\n\nThis is the long form description of my change"
    ).call()
  }
}
