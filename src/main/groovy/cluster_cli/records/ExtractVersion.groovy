package cluster_cli.records

class ExtractVersion {
  static boolean extractVersion(String version, String nature ) {
    if (nature == "Net")
      return true
    else {
      String userHome = System.getProperty("user.home")
      String jarLocation = "${userHome}\\.m2\\repository\\jonkerridge\\cluster_cli"
      String gradleLocation = "${userHome}\\.gradle\\caches\\modules-2\\files-2.1\\jonkerridge\\cluster_cli"
      String folder = gradleLocation + "\\$version"
      if (new File(folder).isDirectory())
        return true
      else {
        folder = jarLocation + "\\$version"
        if (new File(folder).isDirectory())
          return true
        else {
          println "Continuing but unable to check availability of Version $version"
          return true
        }
      }
    }
  }

  static void main(String[] args) {
    String version = VersionControl.versionTag
    if (!extractVersion(version, null)) println "cli_cluster:Version $version needs to downloaded, please modify the gradle.build file"
    else println "Correct version is available: $version"
  }
}
