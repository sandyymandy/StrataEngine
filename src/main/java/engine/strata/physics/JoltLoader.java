package engine.strata.physics;

import electrostatic4j.snaploader.*;
import electrostatic4j.snaploader.filesystem.DirectoryPath;
import electrostatic4j.snaploader.platform.NativeDynamicLibrary;
import electrostatic4j.snaploader.platform.util.PlatformPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JoltLoader {
    public static final Logger LOGGER = LoggerFactory.getLogger("JoltLoader");


    private static boolean loaded = false;

    public static void load() {
        if (loaded) return;

        LibraryInfo info = new LibraryInfo(null, "joltjni", DirectoryPath.USER_DIR);
        NativeBinaryLoader loader = new NativeBinaryLoader(info);

        // Only Windows for now (add more platforms later)
        NativeDynamicLibrary[] libraries = {
                new NativeDynamicLibrary("linux/aarch64/com/github/stephengold", PlatformPredicate.LINUX_ARM_64),
                new NativeDynamicLibrary("linux/armhf/com/github/stephengold", PlatformPredicate.LINUX_ARM_32),
                new NativeDynamicLibrary("linux/x86-64/com/github/stephengold", PlatformPredicate.LINUX_X86_64),
                new NativeDynamicLibrary("osx/aarch64/com/github/stephengold", PlatformPredicate.MACOS_ARM_64),
                new NativeDynamicLibrary("osx/x86-64/com/github/stephengold", PlatformPredicate.MACOS_X86_64),
                new NativeDynamicLibrary("windows/x86-64/com/github/stephengold", PlatformPredicate.WIN_X86_64)
        };

        loader.registerNativeLibraries(libraries).initPlatformLibrary();

        try {
            loader.loadLibrary(LoadingCriterion.CLEAN_EXTRACTION);
            loaded = true;
            LOGGER.info("Jolt JNI natives loaded successfully");
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Jolt JNI natives", e);
        }
    }
}