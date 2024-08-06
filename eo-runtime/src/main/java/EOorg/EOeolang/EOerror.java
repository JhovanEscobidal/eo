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

/*
 * @checkstyle PackageNameCheck (4 lines)
 * @checkstyle TrailingCommentCheck (3 lines)
 */
package EOorg.EOeolang; // NOPMD

import java.util.function.Supplier;
import org.eolang.AtVoid;
import org.eolang.Atom;
import org.eolang.Dataized;
import org.eolang.ExAbstract;
import org.eolang.ExFailure;
import org.eolang.PhDefault;
import org.eolang.Phi;
import org.eolang.VerboseBytesAsString;
import org.eolang.Versionized;
import org.eolang.XmirObject;

/**
 * ERROR.
 *
 * <p>You are NOT supposed to use this object programmatically. It is only
 * used from EO, but not from Java. From Java you just throw
 * {@link ExFailure}. It will be properly caught and converted to the error.
 * Again, DON'T USE THIS OBJECT PROGRAMMATICALLY.
 *
 * @since 0.22
 * @checkstyle TypeNameCheck (5 lines)
 */
@Versionized
@XmirObject(oname = "error")
public final class EOerror extends PhDefault implements Atom {
    /**
     * Ctor.
     */
    @SuppressWarnings("PMD.ConstructorOnlyInitializesOrCallOtherConstructors")
    public EOerror() {
        this.add("message", new AtVoid("message"));
    }

    @Override
    public Phi lambda() {
        throw new ExError(this.take("message"));
    }

    /**
     * Builds error message from the exception.
     *
     * @since 0.40
     */
    public static final class ErrorMsg implements Supplier<String> {
        /**
         * The exception.
         */
        private final Throwable exp;

        /**
         * Ctor.
         *
         * @param exp The exception.
         */
        public ErrorMsg(final Throwable exp) {
            this.exp = exp;
        }

        @Override
        public String get() {
            return ErrorMsg.message(this.exp);
        }

        /**
         * Make a message from an exception.
         * @param exp The exception.
         * @return Message.
         */
        private static String message(final Throwable exp) {
            final StringBuilder ret = new StringBuilder(0);
            if (exp.getMessage() != null) {
                if (!(exp instanceof ExFailure)) {
                    ret.append(exp.getClass().getSimpleName()).append(": ");
                }
                ret.append(exp.getMessage().replace("%", "%%"));
            }
            if (exp.getCause() != null) {
                ret.append("; caused by ").append(ErrorMsg.message(exp.getCause()));
            }
            return ret.toString();
        }
    }

    /**
     * This exception is thrown by the {@link EOerror} object only.
     *
     * <p>You are not supposed to use it anywhere else!</p>
     *
     * @since 0.24
     */
    public static final class ExError extends ExAbstract {

        /**
         * Serialization identifier.
         */
        private static final long serialVersionUID = 1735493012609760997L;

        /**
         * Enclosure.
         */
        private final Phi enc;

        /**
         * Ctor.
         * @param enclosure Enclosure inside the error
         */
        public ExError(final Phi enclosure) {
            super(EOerror.ExError.safeMessage(enclosure));
            this.enc = enclosure;
        }

        /**
         * Take it.
         * @return The enclosed object
         */
        public Phi enclosure() {
            return this.enc;
        }

        /**
         * Retrieve message from enclosure safely.
         * @param enclosure Enclosure.
         * @return String message.
         * @checkstyle IllegalCatchCheck (55 lines)
         */
        @SuppressWarnings("PMD.AvoidCatchingGenericException")
        private static String safeMessage(final Phi enclosure) {
            String result;
            if (enclosure == null) {
                result = "null Phi";
            } else {
                try {
                    final byte[] raw = new Dataized(enclosure).take();
                    result = String.format(
                        "%s(Δ = %s)",
                        enclosure,
                        new VerboseBytesAsString(raw).get()
                    );
                } catch (final Exception first) {
                    try {
                        result = enclosure.toString();
                    } catch (final Exception second) {
                        result = enclosure.getClass().toString();
                    }
                }
            }
            return result;
        }
    }
}
