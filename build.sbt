import com.typesafe.sbt.SbtSite.SiteKeys._
import com.typesafe.sbt.SbtGhPages.GhPagesKeys._
import ReleaseTransformations._
import com.ambiata.promulgate.project.ProjectPlugin.promulgate
import Defaults.{defaultTestTasks, testTaskOptions}
import sbtrelease._

lazy val producer = project.in(file("."))
  .settings(moduleName := "producer")
  .settings(buildSettings)
  .settings(publishSettings)
  .settings(commonSettings)

lazy val buildSettings = Seq(
  organization := "org.atnos",
  scalaVersion := "2.12.1",
  crossScalaVersions := Seq("2.11.8", "2.12.1")
)

def commonSettings = Seq(
  scalacOptions ++= commonScalacOptions,
  scalacOptions in (Compile, doc) := (scalacOptions in (Compile, doc)).value.filter(_ != "-Xfatal-warnings"),
  addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
  addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.3"),
  si2712,
  libraryDependencies ++= si2712Dependency(scalaVersion.value)
) ++ warnUnusedImport ++ prompt

lazy val tagName = Def.setting{
  s"v${if (releaseUseGlobalVersion.value) (version in ThisBuild).value else version.value}"
}

lazy val publishSettings =
  Seq(
  homepage := Some(url("https://github.com/atnos-org/producer")),
  licenses := Seq("MIT" -> url("http://opensource.org/licenses/MIT")),
  scmInfo := Some(ScmInfo(url("https://github.com/atnos-org/producer"), "scm:git:git@github.com:atnos-org/producer.git")),
  autoAPIMappings := true,
  apiURL := Some(url("http://atnos.org/producer/api/")),
  pomExtra := (
    <developers>
      <developer>
        <id>etorreborre</id>
        <name>Eric Torreborre</name>
        <url>https://github.com/etorreborre/</url>
      </developer>
    </developers>
    )
) ++ credentialSettings ++ sharedPublishSettings ++ sharedReleaseProcess

lazy val commonScalacOptions = Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-language:_",
  "-unchecked",
  "-Xfatal-warnings",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Xfuture"
)

lazy val sharedPublishSettings = Seq(
  releaseCrossBuild := true,
  releaseTagName := tagName.value,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := Function.const(false),
  publishTo := Option("Releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2")
) ++ site.settings ++
  ghpages.settings ++
  userGuideSettings

lazy val userGuideSettings =
  Seq(
    GhPagesKeys.ghpagesNoJekyll := false,
    SiteKeys.siteSourceDirectory in SiteKeys.makeSite := target.value / "specs2-reports" / "site",
    includeFilter in SiteKeys.makeSite := "*.html" | "*.css" | "*.png" | "*.jpg" | "*.gif" | "*.js",
    git.remoteRepo := "git@github.com:atnos-org/producer.git"
  )

lazy val sharedReleaseProcess = Seq(
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies
  , inquireVersions
  , runTest
  , setReleaseVersion
  , commitReleaseVersion
  , tagRelease
  , generateWebsite
//  , publishSite
  , publishArtifacts
  , setNextVersion
  , commitNextVersion
  , ReleaseStep(action = Command.process("sonatypeReleaseAll", _), enableCrossBuild = true)
  , pushChanges
  )
) ++
  Seq(
    releaseNextVersion := { v => Version(v).map(_.bumpBugfix.string).getOrElse(versionFormatError) },
    releaseTagName := "PRODUCER-" + releaseVersion.value(version.value)
  ) ++
  testTaskDefinition(generateWebsiteTask, Seq(Tests.Filter(_.endsWith("Website"))))

lazy val publishSite = ReleaseStep { st: State =>
  val st2 = executeStepTask(makeSite, "Making the site")(st)
  executeStepTask(pushSite, "Publishing the site")(st2)
}

lazy val generateWebsiteTask = TaskKey[Tests.Output]("generate-website", "generate the website")
lazy val generateWebsite     = executeStepTask(generateWebsiteTask, "Generating the website", Test)

lazy val warnUnusedImport = Seq(
  scalacOptions in (Compile, console) ~= {_.filterNot("-Ywarn-unused-import" == _)},
  scalacOptions in (Test, console) := (scalacOptions in (Compile, console)).value
)

lazy val credentialSettings = Seq(
  // For Travis CI - see http://www.cakesolutions.net/teamblogs/publishing-artefacts-to-oss-sonatype-nexus-using-sbt-and-travis-ci
  credentials ++= (for {
    username <- Option(System.getenv().get("SONATYPE_USERNAME"))
    password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
  } yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password)).toSeq
)

lazy val prompt = shellPrompt in ThisBuild := { state =>
  val name = Project.extract(state).currentRef.project
  (if (name == "producer") "" else name) + "> "
}

def executeTask(task: TaskKey[_], info: String) = (st: State) => {
  st.log.info(info)
  val extracted = Project.extract(st)
  val ref: ProjectRef = extracted.get(thisProjectRef)
  extracted.runTask(task in ref, st)._1
}

def executeStepTask(task: TaskKey[_], info: String, configuration: Configuration) = ReleaseStep { st: State =>
  executeTask(task, info, configuration)(st)
}

def executeStepTask(task: TaskKey[_], info: String) = ReleaseStep { st: State =>
  executeTask(task, info)(st)
}

def executeTask(task: TaskKey[_], info: String, configuration: Configuration) = (st: State) => {
  st.log.info(info)
  Project.extract(st).runTask(task in configuration, st)._1
}

def testTaskDefinition(task: TaskKey[Tests.Output], options: Seq[TestOption]) =
  Seq(testTask(task))                          ++
    inScope(GlobalScope)(defaultTestTasks(task)) ++
    inConfig(Test)(testTaskOptions(task))        ++
    (testOptions in (Test, task) ++= options)

def testTask(task: TaskKey[Tests.Output]) =
  task := Def.taskDyn {
    Def.task(
      Defaults.allTestGroupsTask(
        (streams in Test).value,
        (loadedTestFrameworks in Test).value,
        (testLoader in Test).value,
        (testGrouping in Test in test).value,
        (testExecution in Test in task).value,
        (fullClasspath in Test in test).value,
        (javaHome in test).value
      )).flatMap(identity)
  }.value

lazy val si2712 =
  scalacOptions ++=
    (if (CrossVersion.partialVersion(scalaVersion.value).exists(_._2 >= 12)) Seq("-Ypartial-unification")
    else Seq())

def si2712Dependency(scalaVersion: String) =
  if (CrossVersion.partialVersion(scalaVersion).exists(_._2 < 12))
    Seq(compilerPlugin("com.milessabin" % ("si2712fix-plugin_"+scalaVersion) % "1.2.0"))
  else
    Seq()

ghreleaseRepoOrg := "atnos-org"
ghreleaseRepoName := "producer"

ghreleaseNotes := { tagName: TagName =>
  // find the corresponding release notes
  val notesFilePath = s"notes/${tagName.replace("PRODUCER-", "")}.markdown"
  try io.Source.fromFile(notesFilePath).mkString
  catch { case t: Throwable => throw new Exception(s"$notesFilePath not found") }
}

// just upload the notes
ghreleaseAssets := Seq()
