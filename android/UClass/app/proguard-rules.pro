# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# 네트워크 관련 클래스 보호
-keep class com.ubase.uclass.network.** { *; }
-keep class com.ubase.uclass.presentation.viewmodel.** { *; }

# Compose 관련
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# FCM 프로가드
-keep class com.google.android.gms.** { *; }
-keep class com.google.firebase.** { *; }

# 카카오, 네이버
-keep class com.kakao.sdk.**.model.* { <fields>; }

# OkHttp 관련
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.*
-dontwarn org.openjsse.**

# GraalVM Native Image 관련 (Android에서는 사용하지 않음)
-dontwarn com.oracle.svm.core.annotate.**
-dontwarn org.graalvm.nativeimage.**
-dontwarn okhttp3.internal.graal.**

# Retrofit2 관련 (with r8 full mode)
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

##---------------Begin: proguard configuration for retrofit2  ----------
-keepattributes Signature, InnerClasses, EnclosingMethod

# Retrofit does reflection on method and parameter annotations.
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Retain service method parameters when optimizing.
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Ignore annotation used for build tooling.
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# Ignore JSR 305 annotations for embedding nullability information.
-dontwarn javax.annotation.**

# Guarded by a NoClassDefFoundError try/catch and only used when on the classpath.
-dontwarn kotlin.Unit

# Top-level functions that can only be used by Kotlin.
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# Platform calls Class.forName on types which do not exist on Android to determine platform.
-dontnote retrofit2.Platform
# Platform used when running on Java 8 VMs. Will not be used at runtime.
#-dontwarn retrofit2.Platform$Java8
# Retain generic type information for use by reflection by converters and adapters.
-keepattributes Signature
# Retain declared checked exceptions for use by a Proxy instance.
-keepattributes Exceptions

# jsoup
-keep public class org.jsoup.**{    public *;}
##---------------End: proguard configuration for retrofit2  ----------

##---------------Begin: proguard configuration for Gson  ----------
# Gson uses generic type information stored in a class file when working with fields. Proguard
# removes such information by default, so configure it to keep all of it.
-keepattributes Signature

# For using GSON @Expose annotation
-keepattributes *Annotation*

# Gson specific classes
-dontwarn sun.misc.**
#-keep class com.google.gson.stream.** { *; }

# Application classes that will be serialized/deserialized over Gson
-keep class com.google.gson.examples.android.model.** { *; }

# Prevent proguard from stripping interface information from TypeAdapterFactory,
# JsonSerializer, JsonDeserializer instances (so they can be used in @JsonAdapter)
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ===== 추가된 TypeToken 관련 ProGuard 규칙 =====
# TypeToken 클래스와 관련 메서드 보호
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# TypeToken의 익명 클래스 보호 (핵심!)
-keep class com.ubase.uclass.network.NetworkAPI$*$* {
    *;
}

# 제네릭 타입 정보 보존 (매우 중요!)
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault

# TypeToken을 사용하는 모든 익명 클래스 보호
-keep class **$*TypeToken* { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }

# NetworkAPI 클래스의 모든 내부 클래스 보호
-keep class com.ubase.uclass.network.NetworkAPI$** { *; }

# 람다와 익명 클래스의 타입 정보 보존
-keepclassmembers class * {
    ** *TypeToken*;
}

# TypeToken 생성자 보호
-keepclassmembers class com.google.gson.reflect.TypeToken {
    <init>();
    <init>(...);
}

# 제네릭 파라미터 보존을 위한 추가 설정
-keep class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

##---------------End: proguard configuration for Gson  ----------

# Needed to keep generic types and @Key annotations accessed via reflection
-keepattributes AnnotationDefault

-keepclassmembers class * {
  @com.google.api.client.util.Key <fields>;
}

# Needed by Guava
# See https://groups.google.com/forum/#!topic/guava-discuss/YCZzeCiIVoI
-dontwarn sun.misc.Unsafe
-dontwarn com.google.common.collect.MinMaxPriorityQueue

# Needed by google-http-client-android when linking against an older platform version
-dontwarn com.google.api.client.extensions.android.**

# Needed by google-api-client-android when linking against an older platform version
-dontwarn com.google.api.client.googleapis.extensions.android.**

# GRPC
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keepclassmembers class io.grpc.okhttp.OkHttpChannelBuilder {
  io.grpc.okhttp.OkHttpChannelBuilder forTarget(java.lang.String);
  io.grpc.okhttp.OkHttpChannelBuilder scheduledExecutorService(java.util.concurrent.ScheduledExecutorService);
  io.grpc.okhttp.OkHttpChannelBuilder sslSocketFactory(javax.net.ssl.SSLSocketFactory);
  io.grpc.okhttp.OkHttpChannelBuilder transportExecutor(java.util.concurrent.Executor);
}
-dontwarn javax.naming.**
-dontwarn sun.misc.Unsafe
-dontwarn com.google.common.**
-dontwarn okio.**
# Ignores: can't find referenced class javax.lang.model.element.Modifier
-dontwarn com.google.errorprone.annotations.**
-keep class io.grpc.internal.DnsNameResolverProvider
-keep class io.grpc.okhttp.OkHttpChannelProvider

-keep class android.support.v8.renderscript.** { *; }
-keep class androidx.renderscript.** { *; }