package org.bytedeco.zlib;

import org.junit.Test;
import static org.bytedeco.zlib.global.zlib.*;

public class UnitTest {
    @Test public void test() {
        System.out.println(zlibVersion().getString());
    }
}

