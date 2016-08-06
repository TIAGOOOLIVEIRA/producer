lazy val effcatsVersion = "1.7.4"
lazy val origamiVersion = "1.0-20160701061933-3ad1315"
lazy val specs2Version  = "3.8.4"

libraryDependencies :=
  effcats ++
  origami ++
  specs2

resolvers ++= Seq(
    Resolver.sonatypeRepo("releases")
  , Resolver.typesafeRepo("releases")
  , Resolver.url("ambiata-oss", new URL("https://ambiata-oss.s3.amazonaws.com"))(Resolver.ivyStylePatterns))

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.7.1")
addCompilerPlugin("com.milessabin" % "si2712fix-plugin_2.11.8" % "1.2.0")

lazy val effcats = Seq(
  "org.atnos" %% "eff-cats" % effcatsVersion)

lazy val origami = Seq(
  "org.atnos" %% "origami-eff-cats" % origamiVersion)

lazy val specs2 = Seq(
    "org.specs2" %% "specs2-core"
  , "org.specs2" %% "specs2-matcher-extra"
  , "org.specs2" %% "specs2-scalacheck"
  , "org.specs2" %% "specs2-html"
  , "org.specs2" %% "specs2-junit").map(_ % specs2Version % "test")


