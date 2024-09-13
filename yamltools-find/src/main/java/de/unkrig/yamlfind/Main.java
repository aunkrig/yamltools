
/*
 * yamltools-find - A command-line tool for analyzing YAML documents
 *
 * Copyright (c) 2024, Arno Unkrig
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this list of conditions and the
 *       following disclaimer.
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 *       following disclaimer in the documentation and/or other materials provided with the distribution.
 *    3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote
 *       products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package de.unkrig.yamlfind;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.snakeyaml.engine.v2.common.FlowStyle;
import org.snakeyaml.engine.v2.nodes.Node;

import de.unkrig.commons.file.ExceptionHandler;
import de.unkrig.commons.file.fileprocessing.FileProcessings;
import de.unkrig.commons.util.CommandLineOptionException;
import de.unkrig.commons.util.CommandLineOptions;
import de.unkrig.commons.util.annotation.CommandLineOption;
import de.unkrig.commons.util.annotation.CommandLineOption.Cardinality;
import de.unkrig.yamlutil.SpecParser;

public
class Main {

	private Charset        inCharset  = StandardCharsets.UTF_8;
	private Charset        outCharset = StandardCharsets.UTF_8;
    private final YamlFind yamlFind  = new YamlFind();
    { this.yamlFind.getDumpSettingsBuilder().setDumpComments(true); }

    /**
     * Print this text and terminate.
     */
    @CommandLineOption public static void
    help() throws IOException {

        CommandLineOptions.printResource(Main.class, "main(String[]).txt", Charset.forName("UTF-8"), System.out);

        System.exit(0);
    }

    /**
     * Input encoding charset (default UTF-8)
     * @main.commandLineOptionGroup Input-Processing
     */
    @CommandLineOption public void
    setInCharset(Charset inCharset) { this.inCharset = inCharset; }

    /**
     * Output encoding charset (default UTF-8)
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption public void
    setOutCharset(Charset outCharset) { this.outCharset = outCharset; }

    // =============================== DumpSettingsBuider settings. ===============================

    /**
     * CR, LF, CRLF or any other value
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption public void
    setBestLineBreak(String value) { this.yamlFind.getDumpSettingsBuilder().setBestLineBreak((
        "CR".equals(value) ? "\r" :
        "LF".equals(value) ? "\n" :
        "CRLF".equals(value) ? "\r\n" :
        value
    )); }
    /**
     * Formatting style for generated documents
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption public void
    setDefaultFlowStyle(FlowStyle value) { this.yamlFind.getDumpSettingsBuilder().setDefaultFlowStyle(value); }
    /**
     * Remove comments while transforming
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption public void
    setStripComments() { this.yamlFind.getDumpSettingsBuilder().setDumpComments(false); }
    /**
     * Number of spaces for the indent in the block flow style (default 2)
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption public void
    setIndent(int value) { this.yamlFind.getDumpSettingsBuilder().setIndent(value); }
    /**
     * Add the indent for sequences to the general indent
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption public void
    setIndentWithIndicator() { this.yamlFind.getDumpSettingsBuilder().setIndentWithIndicator(true); }
    /**
     * Add the specified indent for sequence indicator in the block flow (default 0)
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption public void
    setIndicatorIndent(int value) { this.yamlFind.getDumpSettingsBuilder().setIndicatorIndent(value); }
    /**
     * Don't split long lines
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption public void
    setDontSplitLines() { this.yamlFind.getDumpSettingsBuilder().setSplitLines(false); }
    /**
     * Max width for literal scalars (default 80)
     * @main.commandLineOptionGroup Output-Generation
     */
    @CommandLineOption public void
    setWidth(int value) { this.yamlFind.getDumpSettingsBuilder().setWidth(value); }

    // =============================== End DumpSettingsBuider settings. ===============================

    /**
     * Print the specified elements, each on one line; specify {@code ""} to print the entire document.
     *
     * @main.commandLineOptionGroup Document-Processing
     */
    @CommandLineOption(cardinality = Cardinality.ANY) public void
    addDump(String[] specs) throws IOException {
    	for (String spec : specs) {
    		this.yamlFind.addDump(spec, this.outCharset);
		}
    }
    
    /**
     * Printf the specified elements; specify {@code ""} to print the entire document.
     *
     * @main.commandLineOptionGroup Document-Processing
     */
    @CommandLineOption(cardinality = Cardinality.ANY) public void
    addPrintf(String format, String[] specs) throws IOException {
		this.yamlFind.addPrintf(format, specs);
    }

    public static Node
    yamlDocumentOrFile(String yamlDocumentOrFile) throws IOException, FileNotFoundException {

        try (Reader r = Main.stringOrFileReader(yamlDocumentOrFile)) {
            return SpecParser.loadYaml(r);
        }
    }

    private static Reader
    stringOrFileReader(String value) throws FileNotFoundException {
        return (
            value.startsWith("@")
            ? new InputStreamReader(new FileInputStream(value.substring(1)), StandardCharsets.UTF_8)
            : new StringReader(value)
        );
    }

    /**
     * A command line utility that analyzes YAML documents.
     * <h2>Usage</h2>
     * <dl>
     *   <dt>{@code yamlfind} [ <var>option</var> ... ]</dt>
     *   <dd>
     *     Parse a YAML document from STDIN and analyze it
     *   </dd>
     *   <dt>{@code yamlfind} [ <var>option</var> ... ] !<var>yaml-document</var></dt>
     *   <dd>
     *     Parse the literal <var>yaml-document</var>, and analyze it
     *   </dd>
     *   <dt>{@code yamlfind} [ <var>option</var> ] <var>file</var> ... <var>existing-dir</var></dt>
     *   <dd>
     *     Read the YAML document in each <var>file</var>, and analyze it
     *   </dd>
     * </dl>
     *
     * <h2>Options</h2>
     *
     * <h3>General</h3>
     * <dl>
     * {@main.commandLineOptions}
     * </dl>
     *
     * <h3>Input processing</h3>
     * <dl>
     * {@main.commandLineOptions Input-Processing}
     * </dl>
     *
     * <h3>Document processing</h3>
     * <dl>
     * {@main.commandLineOptions Document-Processing}
     * </dl>
     *
     * <h3>Output generation</h3>
     * <dl>
     * {@main.commandLineOptions Output-Generation}
     * </dl>
     *
     * <h2>Specs</h2>
     * <p>
     *   Many of the options specify a path from the root of the YAML document to a node, as follows:
     * </p>
     * <dl>
     *   <dt>{@code .}<var>identifier</var></dt>
     *   <dt>{@code .(}<var>yaml-document</var>{@code )}</dt>
     *   <dd>Use the map entry with the given key, or the given sequence element, or the given set member.</dd>
     *   <dt>{@code [}<var>0...sequenceSize-1</var>{@code ]}</dt>
     *   <dd>Use the sequence element with the given index.</dd>
     *   <dt>{@code [}<var>-sequenceSize...-1</var>{@code ]}</dt>
     *   <dd>Use the sequence element with the given index plus <var>sequenceSize</var>.</dd>
     *   <dt>{@code []}</dt>
     *   <dd>The sequence element after the last existing.</dd>
     * </dl>
     */
    public static void
    main(String[] args) throws IOException, CommandLineOptionException {

        // Configure a "Main" object from the command line options.
        Main main = new Main();
        args = CommandLineOptions.parse(args, main);

        if (args.length == 1 && args[0].startsWith("!")) {

            // Parse single command line argument as a JSON document, and transform it to STDOUT.
            main.yamlFind.process(new StringReader(args[0].substring(1)));
        } else
        {
        	List<File> files = new ArrayList<>();
        	for (String arg : args) files.add(new File(arg));

        	try {
	        	FileProcessings.process(
	                files,                                       // files
	                main.yamlFind.fileProcessor(main.inCharset), // fileProcessor
	                ExceptionHandler.defaultHandler()            // exceptionHandler
	            );
	        } catch (InterruptedException ie) {
	            throw (InterruptedIOException) new InterruptedIOException().initCause(ie);
	        }
        }
    }
}
