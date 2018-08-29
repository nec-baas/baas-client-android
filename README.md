baas-client-android : NECモバイルバックエンド基盤 Java/Android SDK
===================================================================

NECモバイルバックエンド基盤 Java/Android SDK のソースコードリリース。

* Website: https://nec-baas.github.io/

ビルド手順
----------

### 事前準備

JDK8 および Android SDK をインストールしておくこと。

Android SDK については以下コンポーネントのインストールが必要。

* Android SDK Tools
* Android SDK Platform-tools
* Android SDK Build-tools (最新のもの)
* Android 8.1 (API 27) : SDK Platform
* Android Support Repository
* Google Repository

本ライブラリは SSE Push Java Client ライブラリに依存しているため、先に SSE Push
Java Client のビルドが必要である。以下手順で実施しておくこと。5

    $ cd (ssepush-client-java dir)
    $ mvn install

### ビルド

以下のようにしてビルドを行う。

    $ ./gradlew clean build jar install

ビルドされた jar ファイルは */build/libs/*.jar に生成される。

### SDK の生成

以下のようにして SDK を生成する。

    $ ./gradlew sdk

ビルドとライブラリファイル生成が行われ、sdk/ ディレクトリに成果物が出力される。

### Javadoc の生成手順

    $ ./gradlw alljavadoc

Javadoc は ./build/java-android_reference に生成される。
