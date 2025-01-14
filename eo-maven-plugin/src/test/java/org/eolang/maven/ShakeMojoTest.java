/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2024 Objectionary.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.eolang.maven;

import com.jcabi.xml.XMLDocument;
import com.yegor256.Mktmp;
import com.yegor256.MktmpResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.cactoos.io.ResourceOf;
import org.cactoos.text.TextOf;
import org.eolang.maven.util.HmBase;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.hamcrest.io.FileMatchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test case for {@link ShakeMojo}.
 *
 * @since 0.35.0
 */
@ExtendWith(MktmpResolver.class)
final class ShakeMojoTest {

    /**
     * The key for testing.
     */
    private String key;

    @BeforeEach
    void setUp() {
        this.key = "target/%s/foo/x/main.%s";
    }

    @Test
    void shakesSuccessfully(@Mktmp final Path temp) throws IOException {
        final FakeMaven maven = new FakeMaven(temp);
        final Map<String, Path> res = maven
            .withHelloWorld()
            .with("trackOptimizationSteps", true)
            .execute(new FakeMaven.Shake())
            .result();
        MatcherAssert.assertThat(
            "After successful operation of the ShakeMojo, an XML should appear",
            res,
            Matchers.hasKey(
                String.format("target/%s/foo/x/main/01-remove-refs.xml", ShakeMojo.STEPS)
            )
        );
        MatcherAssert.assertThat(
            "After successful operation of the ShakeMojo, an XMIR should appear",
            res,
            Matchers.hasKey(
                String.format(this.key, ShakeMojo.DIR, AssembleMojo.XMIR)
            )
        );
    }

    @Test
    void getsAlreadyShakenResultsFromCache(@Mktmp final Path temp) throws Exception {
        final TextOf cached = new TextOf(
            new ResourceOf("org/eolang/maven/optimize/main.xml")
        );
        final Path cache = temp.resolve("cache");
        final String hash = "abcdef1";
        new HmBase(cache).save(
            cached,
            Paths.get(ShakeMojo.CACHE)
                .resolve(FakeMaven.pluginVersion())
                .resolve(hash)
                .resolve("foo/x/main.xmir")
        );
        Files.setLastModifiedTime(
            cache
                .resolve(Paths.get(ShakeMojo.CACHE))
                .resolve(FakeMaven.pluginVersion())
                .resolve(hash)
                .resolve("foo/x/main.xmir"),
            FileTime.fromMillis(System.currentTimeMillis() + 50_000)
        );
        new FakeMaven(temp)
            .withHelloWorld()
            .with("cache", cache.toFile())
            .allTojosWithHash(() -> hash)
            .execute(new FakeMaven.Shake());
        MatcherAssert.assertThat(
            "Ready shaken results should be loaded from cache.",
            new XMLDocument(
                new HmBase(temp).load(
                    Paths.get(
                        String.format(
                            this.key,
                            ShakeMojo.DIR,
                            AssembleMojo.XMIR
                        )
                    )
                ).asBytes()
            ),
            Matchers.is(new XMLDocument(cached.asString()))
        );
    }

    @Test
    void skipsAlreadyShaken(@Mktmp final Path temp) throws IOException {
        final FakeMaven maven = new FakeMaven(temp)
            .withHelloWorld()
            .execute(new FakeMaven.Shake());
        final Path path = maven.result().get(
            String.format(this.key, ShakeMojo.DIR, AssembleMojo.XMIR)
        );
        final long mtime = path.toFile().lastModified();
        maven.execute(ShakeMojo.class);
        MatcherAssert.assertThat(
            "Re-shaken of the program should be skipped.",
            path.toFile().lastModified(),
            Matchers.is(mtime)
        );
    }

    @Test
    void shakesIfExpired(@Mktmp final Path temp) throws Exception {
        final FakeMaven maven = new FakeMaven(temp);
        final Path tgt = maven
            .withHelloWorld()
            .execute(new FakeMaven.Shake())
            .result()
            .get(
                String.format(this.key, ShakeMojo.DIR, AssembleMojo.XMIR)
            );
        final long old = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10L);
        if (!tgt.toFile().setLastModified(old)) {
            Assertions.fail(String.format("The last modified attribute can't be set for %s", tgt));
        }
        maven.execute(ShakeMojo.class);
        MatcherAssert.assertThat(
            "We expect that already shaken xmir will be replaced by a new shaken xmir, because the first xmir is outdated and should be updated",
            tgt.toFile().lastModified(),
            Matchers.greaterThan(old)
        );
    }

    @Test
    void savesShakenResultsToCache(@Mktmp final Path temp) throws IOException {
        final Path cache = temp.resolve("cache");
        final String hash = "abcdef1";
        new FakeMaven(temp)
            .withHelloWorld()
            .with("cache", cache.toFile())
            .allTojosWithHash(() -> hash)
            .execute(new FakeMaven.Shake());
        MatcherAssert.assertThat(
            "Shaken results should be saved.",
            cache.resolve(ShakeMojo.CACHE)
                .resolve(FakeMaven.pluginVersion())
                .resolve(hash)
                .resolve("foo/x/main.xmir").toFile(),
            FileMatchers.anExistingFile()
        );
    }

    @Test
    void shakesConcurrentlyWithLotsOfPrograms(@Mktmp final Path temp) throws IOException {
        final FakeMaven maven = new FakeMaven(temp);
        final int total = 20;
        for (int program = 0; program < total; ++program) {
            maven.withHelloWorld();
        }
        final Map<String, Path> res = maven
            .execute(new FakeMaven.Shake())
            .result();
        for (int program = 0; program < total; ++program) {
            MatcherAssert.assertThat(
                "Shaken results of all executed programs must be found.",
                res,
                Matchers.hasKey(
                    String.format(
                        "target/%s/foo/x/main%s.%s",
                        ShakeMojo.DIR,
                        FakeMaven.suffix(program),
                        AssembleMojo.XMIR
                    )
                )
            );
        }
    }

    @Test
    void doesNotCrashesOnError(@Mktmp final Path temp) throws Exception {
        MatcherAssert.assertThat(
            "The program should run without errors.",
            new FakeMaven(temp)
                .withProgram(
                    "+package f\n",
                    "[args] > main",
                    "  seq > @",
                    "    true > x",
                    "    false > x"
                ).with("trackOptimizationSteps", true)
                .execute(new FakeMaven.Shake())
                .result(),
            Matchers.hasKey(
                String.format(this.key, ShakeMojo.DIR, AssembleMojo.XMIR)
            )
        );
    }
}
