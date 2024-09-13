
/*
 * yamltools-util - A library for command-line-base YAML tools
 *
 * Copyright (c) 2023, Arno Unkrig
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

package de.unkrig.yamlutil;

import java.io.Reader;
import java.io.StringWriter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.StreamDataWriter;
import org.snakeyaml.engine.v2.common.FlowStyle;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.composer.Composer;
import org.snakeyaml.engine.v2.nodes.AnchorNode;
import org.snakeyaml.engine.v2.nodes.CollectionNode;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.NodeType;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.SequenceNode;
import org.snakeyaml.engine.v2.nodes.Tag;
import org.snakeyaml.engine.v2.parser.ParserImpl;
import org.snakeyaml.engine.v2.scanner.StreamReader;

import de.unkrig.commons.lang.ExceptionUtil;

public
class SpecParser {

    public static final long serialVersionUID = 1L;

    private static final Pattern MAP_ENTRY_SPEC1       = Pattern.compile("\\.([A-Za-z0-9_\\-]+)");
    private static final Pattern MAP_ENTRY_SPEC2       = Pattern.compile("\\.\\((.*)");
    private static final Pattern SEQUENCE_ELEMENT_SPEC = Pattern.compile("\\[(-?\\d*)]");

    public static
    class SpecMatchException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public SpecMatchException(String message) { super(message); }
    }

    public static
    class SpecSyntaxException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public SpecSyntaxException(String message) { super(message); }
        public SpecSyntaxException(String message, Throwable cause) { super(message, cause); }
    }

    public
    interface SpecHandler {

        /**
         * Designated node is a map (maybe with a "{@link Tag#SET set}" {@link Node#getTag() tag}).
         */
        void handleMapEntry(MappingNode map, Node key);

        /**
         * Designated node is a sequence.
         */
        void handleSequenceElement(SequenceNode sequence, int index);
    }

    public
    interface SpecHandler2 {

    	/**
    	 * Designated node is a scalar.
    	 */
    	void handleScalar(ScalarNode scalar);

    	/**
    	 * Designated node is a sequence.
    	 */
    	void handleSequence(SequenceNode sequence);

    	/**
    	 * Designated node is a map (maybe with a "{@link Tag#SET set}" {@link Node#getTag() tag}).
    	 */
    	void handleMap(MappingNode map);

    	/**
    	 * Designated node is an anchor.
    	 */
    	void handleAnchor(AnchorNode anchor);
    }

    public
    interface SpecHandler3 {
    	void handleNode(Node node);
    }

    /**
     * Parses the <var>spec</var>, locates the relevant node in the <var>root</var> document, and invokes one of the
     * methods of the <var>specHandler</var>.
     * 
     * @throws SpecMatchException A map entry spec was applied to a non-map element
     * @throws SpecMatchException A map entry spec designates a non-existing key
     * @throws SpecMatchException A sequence element spec was applied to a non-sequence element
     * @throws SpecMatchException A sequence index was out-of-range (except for the last segment of the <var>spec</var>)
     * @throws SpecMatchException A set member spec was applied to a non-set element
     * @throws SpecSyntaxException
     */
    public static void
    processSpec(Node root, String spec, SpecHandler specHandler) {

        Node el = root;
        Matcher m;
        SPEC: for (StringBuilder s = new StringBuilder(spec);;) {
            try {

                if (
                    (m = MAP_ENTRY_SPEC1.matcher(s)).lookingAt()     // .<identifier>
                    || (m = MAP_ENTRY_SPEC2.matcher(s)).lookingAt()  // .(<yaml-document>)
                ) {

                    Node key;
                    if (m.pattern() == MAP_ENTRY_SPEC1) {
                        key = new ScalarNode(Tag.STR, m.group(1), ScalarStyle.PLAIN);
                        s.delete(0, m.end());
                    } else
                    if (m.pattern() == MAP_ENTRY_SPEC2) {
                        s.delete(0, 2);
                        key = SpecParser.loadFirst(s);
                        if (s.length() == 0 || s.charAt(0) != ')') throw new SpecSyntaxException("Closing parenthesis missing after map key \"" + toString(key) + "\"");
                        s.delete(0, 1);
                    } else
                    {
                        throw new AssertionError(m.pattern());
                    }
                    
                    switch (el.getNodeType()) {

                    case MAPPING:
                        MappingNode yamlMap = (MappingNode) el;
                        
                        if (s.length() == 0) {
                            specHandler.handleMapEntry(yamlMap, key);
                            return;
                        }
                        
                        for (NodeTuple nt : yamlMap.getValue()) {
                            if (equals(nt.getKeyNode(), key)) {
                                el = nt.getValueNode();
                                continue SPEC;
                            }
                        }
                        throw new SpecMatchException("Map does not contain key \"" + toString(key) + "\"");

                    case SEQUENCE:
                        SequenceNode yamlSequence = (SequenceNode) el;

                        List<Node> elements = yamlSequence.getValue();
                        for (int index = 0; index < elements.size(); index++) {
                            Node sequenceElement = elements.get(index);
                            if (equals(sequenceElement, key)) {
                                if (s.length() == 0) {
                                    specHandler.handleSequenceElement(yamlSequence, index);
                                    return;
                                }
                                
                                el = sequenceElement;
                                continue SPEC;
                            }
                        }
                        throw new SpecMatchException("Sequence does not contain an element \"" + toString(key) + "\"");

                    default:
                    	throw new SpecMatchException("Element is not a map nor a sequence");
                    }
                } else
                if ((m = SEQUENCE_ELEMENT_SPEC.matcher(s)).lookingAt()) {  // [<integer>], []

                    if (el.getNodeType() != NodeType.SEQUENCE) throw new SpecMatchException("Element is not a sequence");
                    SequenceNode yamlSequence = (SequenceNode) el;
                    List<Node> value = yamlSequence.getValue();

                    int index = m.group(1).isEmpty() ? value.size() : Integer.parseInt(m.group(1));
                    if (index < 0) index += value.size();

                    if (m.end() == s.length()) {
                        specHandler.handleSequenceElement(yamlSequence, index);
                        return;
                    }

                    if (index < 0 || index >= value.size()) throw new SpecMatchException("Index " + index + " is out of range; sequence \"" + SpecParser.toString(yamlSequence) + "\" has " + value.size() + " elements");
                    el = value.get(index);
                    assert el != null;
                    s.delete(0, m.end());
                } else
                {
                    throw new SpecSyntaxException("Invalid spec \"" + s + "\"");
                }
            } catch (RuntimeException e) {
                throw ExceptionUtil.wrap(
                    "Applying spec \"" + spec + "\" at offset " + (spec.length() - s.length()) + " on \"" + toString(el) + "\"",
                    e
                );
            }
        }
    }

    public static void
	processSpec(Node root, String spec, SpecHandler3 specHandler3) {

    	if ("".equals(spec)) {
    		specHandler3.handleNode(root);
    		return;
    	}

    	SpecParser.processSpec(root, spec, new SpecHandler() {

            @Override public void
            handleMapEntry(MappingNode map, Node key) {
            	for (NodeTuple mapElement : map.getValue()) {
            		if (SpecParser.equals(mapElement.getKeyNode(), key)) {
            			specHandler3.handleNode(mapElement.getValueNode());
            			return;
            		}
            	}
            	throw new SpecMatchException("Map \"" + SpecParser.toString(map) + "\" lacks key \"" + SpecParser.toString(key) + "\"");
            }

            @Override public void
            handleSequenceElement(SequenceNode sequence, int index) {
            	List<Node> sequenceElements = sequence.getValue();
            	if (index < 0 || index >= sequenceElements.size()) throw new SpecMatchException("Index " + index + " out of range");
            	specHandler3.handleNode(sequenceElements.get(index));
            }
        });
    }

    /**
     * Loads the first node of a YAML document and removes the parsed characters from the <var>sb</sb>.
     */
    private static Node
    loadFirst(StringBuilder sb) {
    	LoadSettings settings = LoadSettings.builder().setAllowDuplicateKeys(true).build();

    	ParserImpl      parser   = new ParserImpl(settings, new StreamReader(settings, sb.toString()));
    	Composer        composer = new Composer(settings, parser);

    	// Drop the STREAM-START event.
    	parser.next();

    	Node node = composer.next();

    	sb.delete(0, node.getEndMark().get().getIndex());

    	return node;
    }

    /**
     * Parses the <var>spec</var>, locates the relevant node in the <var>root</var> document, and invokes one of the
     * methods of the <var>specHandler2</var>.
     * 
     * @throws SpecMatchException A map entry spec was applied to a non-map element
     * @throws SpecMatchException A map entry spec designates a non-existing key
     * @throws SpecMatchException A sequence element spec was applied to a non-sequence element
     * @throws SpecMatchException A sequence index was out-of-range
     * @throws SpecMatchException A set member spec was applied to a non-set element
     * @throws SpecSyntaxException
     */
    public static void
    processSpec(Node root, String spec, SpecHandler2 specHandler2) {
    	processSpec(root, spec, new SpecHandler3() {

			@Override public void
			handleNode(Node node) {
				switch (node.getNodeType()) {
				
				case SCALAR:
					specHandler2.handleScalar((ScalarNode) node);
					break;
					
				case SEQUENCE:
					specHandler2.handleSequence((SequenceNode) node);
					break;
					
				case MAPPING:
					specHandler2.handleMap((MappingNode) node);
					break;
					
				case ANCHOR:
					specHandler2.handleAnchor((AnchorNode) node);
					break;
				}
			}
		});
    }

    /**
     * Loads a YAML document that defines exactly one node. 
     */
   public static Node
   loadYaml(Reader r) {
       LoadSettings settings = LoadSettings.builder().setAllowDuplicateKeys(true).build();

       ParserImpl      parser   = new ParserImpl(settings, new StreamReader(settings, r));
       Composer        composer = new Composer(settings, parser);

       return composer.getSingleNode().get();
   }

    /**
     * @return The YAML document in "FLOW" (single-line) style; useful e.g. for generating error messages
     */
    public static String
    toString(Node node) {
        String result = toString(node, DumpSettings.builder().setDefaultFlowStyle(FlowStyle.FLOW).build());
        result = result.trim();
        if (result.length() > 30) result = result.substring(0, 20) + "...";
        return result;
    }

    /**
     * @return The YAML document
     */
    public static String
    toString(Node node, DumpSettings dumpSettings) {
    	class StreamToStringWriter extends StringWriter implements StreamDataWriter {}
        StreamToStringWriter stsw = new StreamToStringWriter();
        new Dump(dumpSettings).dumpNode(node, stsw);
        return stsw.toString();
    }

    public static boolean
    equals(Node a, Node b) {

        if (a == b) return true;

        if (a instanceof CollectionNode && b instanceof CollectionNode) {
            List<?> aValue = ((CollectionNode<?>) a).getValue();
            List<?> bValue = ((CollectionNode<?>) b).getValue();
            int size = aValue.size();
            if (size != bValue.size()) return false;
            for (int i = 0; i < size; i++) {
                Object av = aValue.get(i);
                Object bv = bValue.get(i);
                if (av instanceof NodeTuple && bv instanceof NodeTuple) {
                    Node aElementKey   = ((NodeTuple) av).getKeyNode();
                    Node aElementValue = ((NodeTuple) av).getValueNode();
                    Node bElementKey   = ((NodeTuple) bv).getKeyNode();
                    Node bElementValue = ((NodeTuple) bv).getValueNode();
                    
                    if (!(
                        equals(aElementKey, bElementKey)
                        && equals(aElementValue, bElementValue)
                    )) return false;
                } else
                if (av instanceof Node && bv instanceof Node) {
                    if (!equals((Node) av, (Node) bv)) return false;
                } else
                {
                    return false;
                }
            }
            return true;
        } else
        if (a instanceof ScalarNode && b instanceof ScalarNode) {
            return ((ScalarNode) a).getValue().equals(((ScalarNode) b).getValue());
        } else
        {
            return false;
        }
    }
}
