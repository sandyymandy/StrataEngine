package engine.strata.api.mod;

import java.util.List;
import java.util.Map;

public record ModMetadata(
        String namespace,
        String name,
        String version,
        String api_version,
        String description,
        String author,
        Map<String, List<String>> entrypoints,
        Map<String, String> depends
) {}