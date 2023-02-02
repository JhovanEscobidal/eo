/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2023 Objectionary.com
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

import com.jcabi.log.Logger;
import com.jcabi.log.VerboseProcess;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.maven.plugin.testing.stubs.MavenProjectStub;
import org.cactoos.Input;
import org.cactoos.Output;
import org.cactoos.io.InputOf;
import org.cactoos.io.OutputTo;
import org.cactoos.io.ResourceOf;
import org.cactoos.io.TeeInput;
import org.cactoos.list.Joined;
import org.cactoos.list.ListOf;
import org.cactoos.scalar.LengthOf;
import org.cactoos.text.IsEmpty;
import org.cactoos.text.TextOf;
import org.eolang.jucs.ClasspathSource;
import org.eolang.maven.util.Home;
import org.eolang.maven.util.Walk;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.yaml.snakeyaml.Yaml;

/**
 * Integration test for simple snippets.
 *
 * This test will/may fail if you change something in {@code to-java.xsl}
 * or some other place where Java sources are generated. This happens
 * because this test relies on {@code eo-runtime.jar}, which it finds in your local
 * Maven repository. This file is supposed to be generated by a previous run
 * of Maven, but will not exist at the first run. Thus, when changes are made
 * it is recommended to disable this test. Then, when new {@code eo-runtime.jar} is
 * released, you enable this test again.
 *
 * @since 0.1
 * @todo #1107:30m Method `jdkExecutable` is duplicated in eo-runtime.
 *  Find a way to make it reusable (i.e making it part of
 *  VerboseProcess) and remove it from MainTest.
 */
@ExtendWith(OnlineCondition.class)
final class SnippetTest {

    /**
     * Temp dir.
     * @checkstyle VisibilityModifierCheck (5 lines)
     */
    @TempDir
    public Path temp;

    @Disabled
    @ParameterizedTest
    @SuppressWarnings("unchecked")
    @ClasspathSource(value = "org/eolang/maven/snippets/", glob = "**.yaml")
    void runsAllSnippets(final String yml) throws Exception {
        final Yaml yaml = new Yaml();
        final Map<String, Object> map = yaml.load(yml);
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final int result = SnippetTest.run(
            this.temp,
            new InputOf(String.format("%s\n", map.get("eo"))),
            (List<String>) map.get("args"),
            new InputOf(map.get("in").toString()),
            new OutputTo(stdout)
        );
        MatcherAssert.assertThat(
            String.format("'%s' returned wrong exit code", yml),
            result, Matchers.equalTo(map.get("exit"))
        );
        Logger.debug(this, "Stdout: \"%s\"", stdout.toString());
        for (final String ptn : (Iterable<String>) map.get("out")) {
            MatcherAssert.assertThat(
                String.format("'%s' printed something wrong", yml),
                new String(stdout.toByteArray(), StandardCharsets.UTF_8),
                Matchers.matchesPattern(
                    Pattern.compile(ptn, Pattern.DOTALL | Pattern.MULTILINE)
                )
            );
        }
    }

    /**
     * Compile EO to Java and run.
     * @param tmp Temp dir
     * @param code EO sources
     * @param args Command line arguments
     * @param stdin The input
     * @param stdout Where to put stdout
     * @return All Java code
     * @throws Exception If fails
     * @checkstyle ParameterNumberCheck (5 lines)
     */
    @SuppressWarnings({"unchecked", "PMD.ExcessiveMethodLength"})
    private static int run(
        final Path tmp,
        final Input code,
        final List<String> args,
        final Input stdin,
        final Output stdout
    ) throws Exception {
        final Path src = tmp.resolve("src");
        new Home(src).save(code, Paths.get("code.eo"));
        final Path target = tmp.resolve("target");
        final Path foreign = target.resolve("eo-foreign.json");
        new Moja<>(RegisterMojo.class)
            .with("foreign", target.resolve("eo-foreign.json").toFile())
            .with("foreignFormat", "json")
            .with("sourcesDir", src.toFile())
            .execute();
        new Moja<>(DemandMojo.class)
            .with("foreign", foreign.toFile())
            .with("foreignFormat", "json")
            .with("objects", new ListOf<>("org.eolang.bool"))
            .execute();
        final Path home = Paths.get(
            System.getProperty(
                "runtime.path",
                Paths.get("").toAbsolutePath().resolve("eo-runtime").toString()
            )
        );
        final OyFake objectionary = new OyFake(
            name -> {
                if (name.contains("collections")) {
                    return new ResourceOf(
                        String.format("%s.eo", name.replace(".", "/"))
                    );
                }
                return new InputOf(
                    home.resolve(
                        String.format(
                            "src/main/eo/%s.eo",
                            name.replace(".", "/")
                        )
                    )
                );
            },
            name -> {
                if (name.contains("collections")) {
                    return !new IsEmpty(
                        new TextOf(
                            new ResourceOf(
                                String.format("%s.eo", name.replace(".", "/"))
                            )
                        )
                    ).value();
                }
                return Files.exists(
                    home.resolve(
                        String.format(
                            "src/main/eo/%s.eo",
                            name.replace(".", "/")
                        )
                    )
                );
            }
        );
        new Moja<>(AssembleMojo.class)
            .with("ignoreTransitive", true)
            .with("outputDir", target.resolve("out").toFile())
            .with("targetDir", target.toFile())
            .with("foreign", foreign.toFile())
            .with("foreignFormat", "json")
            .with("placed", target.resolve("list").toFile())
            .with("objectionary", objectionary)
            .with("central", Central.EMPTY)
            .execute();
        final Path generated = target.resolve("generated");
        new Moja<>(TranspileMojo.class)
            .with("project", new MavenProjectStub())
            .with("targetDir", target.toFile())
            .with("generatedDir", generated.toFile())
            .with("foreign", foreign.toFile())
            .with("transpiled", target.resolve("transpiled.csv").toFile())
            .with("foreignFormat", "json")
            .execute();
        final Path classes = target.resolve("classes");
        classes.toFile().mkdir();
        final String cpath = String.format(
            ".%s%s",
            File.pathSeparatorChar,
            System.getProperty(
                "runtime.jar",
                Paths.get(System.getProperty("user.home")).resolve(
                    String.format(
                        ".m2/repository/org/eolang/eo-runtime/%s/eo-runtime-%1$s.jar",
                        "1.0-SNAPSHOT"
                    )
                ).toString()
            )
        );
        SnippetTest.exec(
            String.format(
                "%s -encoding utf-8 %s -d %s -cp %s",
                SnippetTest.jdkExecutable("javac"),
                new Walk(generated).stream()
                    .map(Path::toAbsolutePath)
                    .map(Path::toString)
                    .collect(Collectors.joining(" ")),
                classes,
                cpath
            ),
            generated
        );
        SnippetTest.exec(
            String.join(
                " ",
                new Joined<String>(
                    new ListOf<>(
                        SnippetTest.jdkExecutable("java"),
                        "-Dfile.encoding=utf-8",
                        "-cp",
                        cpath,
                        "org.eolang.Main"
                    ),
                    args
                )
            ),
            classes,
            stdin,
            stdout
        );
        return 0;
    }

    /**
     * Run some command and print out the output.
     *
     * @param cmd The command
     * @param dir The home dir
     */
    private static void exec(final String cmd, final Path dir) {
        SnippetTest.exec(
            cmd, dir, new InputOf(""),
            new OutputTo(new ByteArrayOutputStream())
        );
    }

    /**
     * Run some command and print out the output.
     *
     * @param cmd The command
     * @param dir The home dir
     * @param stdin Stdin
     * @param stdout Stdout
     * @checkstyle ParameterNumberCheck (5 lines)
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private static void exec(final String cmd, final Path dir,
        final Input stdin, final Output stdout
    ) {
        Logger.debug(SnippetTest.class, "+%s", cmd);
        try {
            final Process proc = new ProcessBuilder()
                .command(cmd.split(" "))
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start();
            new LengthOf(
                new TeeInput(
                    stdin,
                    new OutputTo(proc.getOutputStream())
                )
            ).value();
            try (VerboseProcess vproc = new VerboseProcess(proc)) {
                new LengthOf(
                    new TeeInput(
                        new InputOf(vproc.stdout()),
                        stdout
                    )
                ).value();
            }
            // @checkstyle IllegalCatchCheck (1 line)
        } catch (final Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Locate executable inside JAVA_HOME.
     * @param name Name of executable.
     * @return Path to java executable.
     */
    private static String jdkExecutable(final String name) {
        final String result;
        final String relative = "%s/bin/%s";
        final String property = System.getProperty("java.home");
        if (property == null) {
            final String environ = System.getenv("JAVA_HOME");
            if (environ == null) {
                result = name;
            } else {
                result = String.format(relative, environ, name);
            }
        } else {
            result = String.format(relative, property, name);
        }
        return result;
    }

}
