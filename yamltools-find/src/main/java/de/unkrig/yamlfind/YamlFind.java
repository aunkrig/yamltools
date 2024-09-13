
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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.Formatter;
import java.util.List;

import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.api.DumpSettingsBuilder;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.StreamDataWriter;
import org.snakeyaml.engine.v2.api.YamlOutputStreamWriter;
import org.snakeyaml.engine.v2.api.lowlevel.Compose;
import org.snakeyaml.engine.v2.nodes.Node;

import de.unkrig.commons.file.contentsprocessing.ContentsProcessor;
import de.unkrig.commons.file.fileprocessing.FileContentsProcessor;
import de.unkrig.commons.file.fileprocessing.FileProcessor;
import de.unkrig.commons.lang.AssertionUtil;
import de.unkrig.commons.lang.protocol.Consumer;
import de.unkrig.commons.lang.protocol.ProducerWhichThrows;
import de.unkrig.commons.nullanalysis.Nullable;
import de.unkrig.yamlutil.SpecParser;
import de.unkrig.yamlutil.SpecParser.SpecHandler3;

public
class YamlFind {

    static {
        AssertionUtil.enableAssertionsForThisClass();
    }

    private final DumpSettingsBuilder  dumpSettingsBuilder = DumpSettings.builder();
    private final List<Consumer<Node>> documentConsumers   = new ArrayList<>();

    /**
     * @return The modifiable {@link DumpSettingsBuilder} that will take effect for the next {@link #process(Reader)}
     *         operation
     */
    public DumpSettingsBuilder
    getDumpSettingsBuilder() { return this.dumpSettingsBuilder; }

    /**
     * {@link #process(Reader)} will dump the node specified by the <var>spec</var>.
     */
    public void
    addDump(String spec, Charset outCharset) {
    	this.documentConsumers.add(root -> {
			SpecParser.processSpec(root, spec, new SpecHandler3() {

				@Override public void
				handleNode(Node node) {
					YamlFind.this.dump(node, System.out, outCharset);
				}
    		});
    	});
    }
    
    /**
     * {@link #process(Reader)} will {@link Formatter printf} the node specified by the <var>specs</var>.
     */
    public void
    addPrintf(String format, String[] specs) {
    	this.documentConsumers.add(root -> {
    		Object[] args = new Object[specs.length];
    		for (int i = 0; i < specs.length; i++) {
    			final int ii = i;
    			SpecParser.processSpec(root, specs[i], new SpecHandler3() {
    				@Override public void handleNode(Node node) { args[ii] = SpecParser.toString(node); }
    			});
    		}
    		System.out.printf(format, args);
    	});
    }

    public void
    process(Reader in) throws IOException {

        // Read the document from the reader.
        LoadSettings settings = LoadSettings.builder().setAllowDuplicateKeys(true).setParseComments(true).build();

        Node yamlDocument = new Compose(settings).composeReader(in).get();

        for (Consumer<Node> dm : YamlFind.this.documentConsumers) {
            dm.consume(yamlDocument);
        }
    }

    /**
     * Writes the given <var>node</var> to the given {@link OutputStream}, as configured by the {@link
     * #getDumpSettingsBuilder()}
     * 
     * @see DumpSettingsBuilder
     */
    public void
    dump(Node node, OutputStream out, Charset outCharset) {

        this.dump(
    		node,
    		new YamlOutputStreamWriter(out, outCharset) {
	        	@Override public void processIOException(@Nullable IOException ioe) { throw new RuntimeException(ioe); }
	        }
		);
    }

    /**
     * Writes the given <var>node</var> to the given {@link StreamDataWriter}, as configured by the {@link
     * #getDumpSettingsBuilder()}
     * 
     * @see DumpSettingsBuilder
     */
	public void
	dump(Node node, StreamDataWriter osw) {
		Dump dump = new Dump(this.dumpSettingsBuilder.build());
		dump.dumpNode(node, osw);
	}

    public ContentsProcessor<Void>
    contentsProcessor(Charset inCharset) {

        return new ContentsProcessor<Void>() {
            
            @Override @Nullable public Void
            process(
        		String path,
        		InputStream                                                       inputStream,
        		@Nullable Date                                                    lastModifiedDate,
        		long                                                              size,
        		long                                                              crc32,
        		ProducerWhichThrows<? extends InputStream, ? extends IOException> opener
			) throws IOException {
            	YamlFind.this.process(new InputStreamReader(inputStream, inCharset));
            	return null;
			}
        };
    }

    public FileProcessor<Void>
    fileProcessor(Charset inCharset) {
        return new FileContentsProcessor<Void>(this.contentsProcessor(inCharset));
    }
}
