-dontwarn *

-allowaccessmodification

# Keep line numbers for crash reports; the mapping file is uploaded by CI to retrace them
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Mantém os atributos de anotações e assinaturas genéricas (essencial para Room e Reflection)
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# UnobfuscatorCache derives each cache key from the calling Unobfuscator method name found in
# the stack trace, so these methods must keep their names and must not be inlined
-keepclassmembers class com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator {
     *;
}

# FeatureLoader instantiates plugins via reflection with:
# getConstructor(ClassLoader.class, SharedPreferences.class)
# Keep only the constructor signature; class names can still be obfuscated.
-keepclassmembers class * extends com.wmods.wppenhacer.xposed.core.Feature {
     public <init>(java.lang.ClassLoader,android.content.SharedPreferences);
}

# keeps components intact
-keepclassmembers class com.wmods.wppenhacer.xposed.core.components.** {
        *;
}

-repackageclasses 'wae'

# Xposed Init entrypoints
-keep class com.wmods.wppenhacer.WppXposed* { *; }

# jStyleParser (CustomView CSS): CSSFactory loads its default implementations with
# Class.forName and calls getInstance() / the no-arg constructor reflectively
-keep class cz.vutbr.web.csskit.TermFactoryImpl { public static *** getInstance(); }
-keep class cz.vutbr.web.csskit.RuleFactoryImpl { public static *** getInstance(); }
-keep class cz.vutbr.web.domassign.SupportedCSS3 { public static *** getInstance(); }
-keep class cz.vutbr.web.csskit.ElementMatcherSafeStd { <init>(); }
-keep class cz.vutbr.web.domassign.SingleMapNodeData { <init>(); }
# DeclarationTransformerImpl resolves "process" + CamelCase(property) via getDeclaredMethod
-keep class cz.vutbr.web.domassign.DeclarationTransformerImpl {
    public static *** getInstance();
    private boolean process*(cz.vutbr.web.css.Declaration, java.util.Map, java.util.Map);
}
# CSSToken.TypeMapper reads the ANTLR token type constants by field name
-keepclassmembers class cz.vutbr.web.csskit.antlr4.** {
    public static final int *;
}

# Avoid fragment obfuscation/shrinking because some are instantiated by class name
# (Preference XML app:fragment, FragmentManager reflection, deep links, etc.).
-keep class * extends androidx.fragment.app.Fragment {
     public <init>();
}

-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void check*(...);
    public static void throw*(...);
}
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
}
