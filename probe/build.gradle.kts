plugins { application }
dependencies {
    implementation("com.github.TeamNewPipe:NewPipeExtractor:6cb36e982311dc1c529997bc329013afe75f37d1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
application { mainClass.set("Probe") }
