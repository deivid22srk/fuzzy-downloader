// O AGP 9 traz Kotlin embutido, mas a versão do compilador vem do
// kotlin-gradle-plugin que ele carrega (2.2.10). O plugin do Compose precisa
// casar exatamente com a versão do compilador, então forçamos o KGP 2.4.21 no
// classpath do build. É a versão usada pelo `org.jetbrains.kotlin.plugin.compose`.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
