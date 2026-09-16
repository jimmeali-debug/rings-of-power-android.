package com.jimmeali.ringsofpower.audio;

import java.io.IOException;
import java.io.InputStream;

public interface AudioAssetSource {
    InputStream open(String relativePath) throws IOException;
}
