package com.jimmeali.ringsofpower.audio;

public enum AudioAssetKind {
    MUSIC("music", 0, 20),
    NORMAL_SFX("normal_sfx", 0, 38),
    SPECIAL_SFX("special_sfx", 0x5A, 0x60);

    private final String manifestName;
    private final int minimumId;
    private final int maximumId;

    AudioAssetKind(String manifestName, int minimumId, int maximumId) {
        this.manifestName = manifestName;
        this.minimumId = minimumId;
        this.maximumId = maximumId;
    }

    public String manifestName() {
        return manifestName;
    }

    public boolean acceptsId(int id) {
        if (id < minimumId || id > maximumId) {
            return false;
        }
        return this != MUSIC || (id != 18 && id != 19);
    }

    static AudioAssetKind parse(String value) {
        for (AudioAssetKind kind : values()) {
            if (kind.manifestName.equals(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown audio asset kind: " + value);
    }
}
