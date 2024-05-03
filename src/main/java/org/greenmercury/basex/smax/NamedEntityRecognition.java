package org.greenmercury.basex.smax;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.basex.query.QueryException;
import org.basex.util.options.EnumOption;
import org.basex.util.options.NumberOption;
import org.basex.util.options.Option;
import org.basex.util.options.Options;
import org.basex.util.options.StringOption;
import org.greenmercury.basex.smax.ner.TrieNER;
import org.greenmercury.smax.Balancing;
import org.greenmercury.smax.SmaxDocument;
import org.greenmercury.smax.SmaxElement;
import org.greenmercury.smax.SmaxException;
import org.greenmercury.smax.convert.DomElement;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;

/**
 * A SMAX document transformer that inserts markup around named entities specified by a grammar.
 * This is used in the <code>namedEntityRecognition</code> function, see the <code>Functions</code> class.
 *<p>
 * The transformer takes the following parameters:
 * <ul>
 *   <li>grammar A string containing the grammar that specifies the named entities.
 *       The named entities are defined by rules on separate lines of the form:
 *       <code>id &lt;- entity1 entity2 ...</code>
 *       where the entities are separated by tab characters.</li>
 *   <li>matchNodeTemplate The template for the XML element that is inserted for each content fragment that matches a named entity.
 *       This template must have one empty attribute, which will be filled with the id's of the matched named entities, separated by tab characters.
 *       There can be more than one id if the same entity occurs in different rules in the grammar.</li>
 *   <li>options A map with options. The following options are recognized:
 *     <ul>
 *       <li>word-chars Characters that may appear in a word (next to letters and digits).
 *           They are significant for matching. Default is "", so spaces are not included.</li>
 *       <li>no-word-before Characters that may not immediately follow a word (next to letters and digits).
 *           They cannot follow the end of a match. Default is "".</li>
 *       <li>no-word-after Characters that may not immediately precede a word (next to letters and digits).
 *           Matches can only start on a letter or digit, and not after noWordAfter characters. Default is "".</li>
 *       <li>case-insensitive-min-length The minimum entity-length for case-insensitive matching.
 *           Text fragments larger than this will be scanned case-insensitively.
 *           This prevents short words to be recognized as abbreviations.
 *           Set to -1 to always match case-sensitive. Set to 0 to always match case-insensitive.
 *           Default is -1.</li>
 *       <li>fuzzy-min-length The minimum entity-length for fuzzy matching.
 *           Text fragments larger than this may contain characters that are not significant for the trie.
 *           This prevents short words with noise to be recognized as abbreviations.
 *           Set to -1 to match exact. Set to 0 to match fuzzy.
 *           Default is -1.</li>
 *       <li>balancing The SMAX balancing strategy that is used when an element for a recognized entity is inserted.</li>
 *     </ul>
 *   </li>
 * </ul>
 *<p>
 * Suppose "THE" and "CF" are entities in the grammar, probably abbreviations that should be recognized.
 * Setting caseInsensitiveMinLength to 4 prevents the scanner from recognizing "THE" in "Do the right thing".
 * Setting fuzzyMinLength to 3 prevents the scanner from recognizing "CF" in "C.F. Gauss was a German mathematician".
 * With these settings, "rsvp" would be recognized in "Put an r.s.v.p. at the end", provided that '.' is not an in-word character.
 *<p>
 * All sequences of whitespace characters will be treated like a single space,
 * both in the grammar input and the text that is scanned for named entities.
 *<p>
 * @see <a href="https://en.wikipedia.org/wiki/Named-entity_recognition">Wikipedia: Named Entity Recognition</a>
 * @author Rakensi
 */
public class NamedEntityRecognition
{

  //Node insertion template.
  private SmaxElement matchElementTemplate;
  // Name of the attribute that will hold the id's that are found for a named entity.
  private String attributeName;

  // The minimum entity-lengths for case-insensitive or fuzzy matching.
  private int caseInsensitiveMinLength;
  private int fuzzyMinLength;

  // Special characters, see parameter description.
  private String wordChars;
  private String noWordBefore;
  private String noWordAfter;

  // Balancing strategy to use when inserting elements into a SMAX document.
  private Balancing balancing;

  // The TrieNER instance used for scanning.
  private TrieNER triener = null;

  // The (sub-)document that is being transformed.
  private SmaxDocument transformedDocument;

  /**
   * The constructor compiles the named entities grammar.
   */
  public NamedEntityRecognition(String grammar, Node matchElementTemplate, NEROptions options)
  throws QueryException
  {
    if (grammar == null) grammar = "";
    this.setMatchNodeTemplate(matchElementTemplate);
    caseInsensitiveMinLength = options.get(NEROptions.CASE_INSENSITIVE_MIN_LENGTH);
    fuzzyMinLength = options.get(NEROptions.FUZZY_MIN_LENGTH);
    wordChars = options.get(NEROptions.WORD_CHARS);
    noWordBefore = options.get(NEROptions.NO_WORD_BEFORE);
    noWordAfter = options.get(NEROptions.NO_WORD_AFTER);
    balancing = options.get(NEROptions.BALANCING);
    initTrieNER();
    readGrammar(grammar);
  }

  /**
   * The constructor compiles the named entities grammar.
   */
  public NamedEntityRecognition(URL grammar, Node matchElementTemplate, NEROptions options)
  throws QueryException
  {
    this.setMatchNodeTemplate(matchElementTemplate);
    caseInsensitiveMinLength = options.get(NEROptions.CASE_INSENSITIVE_MIN_LENGTH);
    fuzzyMinLength = options.get(NEROptions.FUZZY_MIN_LENGTH);
    wordChars = options.get(NEROptions.WORD_CHARS);
    noWordBefore = options.get(NEROptions.NO_WORD_BEFORE);
    noWordAfter = options.get(NEROptions.NO_WORD_AFTER);
    balancing = options.get(NEROptions.BALANCING);
    initTrieNER();
    readGrammar(grammar);
  }

  private void setMatchNodeTemplate(Node matchDomElementTemplate)
  throws QueryException
  {
    if (matchDomElementTemplate.getNodeType() != Node.ELEMENT_NODE) {
      throw new QueryException("The match-element template node must be an element.");
    }
    try {
      this.matchElementTemplate = DomElement.toSmax((Element)matchDomElementTemplate).getMarkup();
    } catch (SmaxException e) {
      throw new QueryException(e);
    }
    Attributes attributes = this.matchElementTemplate.getAttributes();
    for (int i = 0; i < attributes.getLength(); ++i) {
      if (attributes.getValue(i) == null || attributes.getValue(i).length() == 0) {
        if (this.attributeName != null) {
          throw new QueryException("The match-element template must have exactly one empty attribute."+
              " Found "+this.attributeName+" and "+attributes.getQName(i));
        }
        this.attributeName = attributes.getQName(i);
      }
    }
    if (this.attributeName == null) {
      throw new QueryException("The match node template must have exactly one empty attribute. Found none.");
    }
  }

  private void initTrieNER()
  {
    triener = new TrieNER(wordChars, noWordBefore, noWordAfter) {
      @Override
      public void match(CharSequence text, int start, int end, List<String> ids) {
        SmaxElement matchElement = matchElementTemplate.shallowCopy();
        try
        {
          matchElement.setAttribute(attributeName, String.join("\t", ids));
        }
        catch (SmaxException e)
        {
          throw new RuntimeException("This is not supposed to happen.", e);
        }
        transformedDocument.insertMarkup(matchElement, balancing, start, end);
      }
      @Override
      public void noMatch(CharSequence text, int start, int end) {
     // No action is needed.
      }
    };
  }

  private void readGrammar(String grammar) throws QueryException
  {
    try (
        StringReader grammarStringReader = new StringReader(grammar);
        BufferedReader grammarReader = new BufferedReader(grammarStringReader);
    ) {
      readGrammar(grammarReader);
    } catch (IOException e) {
      throw new QueryException(e);
    }
  }

  private void readGrammar(URL grammar) throws QueryException
  {
    try (
        InputStreamReader grammarStreamReader = new InputStreamReader(grammar.openStream());
        BufferedReader grammarReader = new BufferedReader(grammarStreamReader);
    ) {
      readGrammar(grammarReader);
    } catch (IOException e) {
      throw new QueryException(e);
    }
  }

  private void readGrammar(BufferedReader grammarReader) throws QueryException
  {
    String line;
    int lineNumber = 0;
    try {
      while ((line = grammarReader.readLine()) != null) {
        ++lineNumber;
        if (line.trim().length() > 0) {
          String[] parts = line.split("\\s*<-\\s*", 2);
          if (parts.length != 2) {
            throw new QueryException("Bad trie syntax in line "+lineNumber+": "+line+
                "\n\tEvery line must contain two parts separated by '<-'.");
          }
          if (parts[1].equals("")) {
            throw new QueryException("Bad trie syntax in line "+lineNumber+": "+line+
                "\n\tThe second part of a rule must not be empty).");
          }
          String nttid = parts[0];
          parts = parts[1].split("\\t");
          for (int i = 0; i < parts.length; ++i) {
            String nntt = parts[i];
            triener.getTrie().put(nntt, nttid);
          }
        }
      }
    } catch (IOException e) {
      throw new QueryException(e);
    }
  }

  public void scan(SmaxDocument document) {
    transformedDocument = document;
    triener.scan(document.getContent(), caseInsensitiveMinLength, fuzzyMinLength);
  }


  /**
   * Options for named entity recognition.
   */
  public static final class NEROptions extends Options {
    public static final NumberOption CASE_INSENSITIVE_MIN_LENGTH = new NumberOption("case-insensitive-min-length", -1);
    public static final NumberOption FUZZY_MIN_LENGTH = new NumberOption("fuzzy-min-length", -1);
    public static final StringOption WORD_CHARS = new StringOption("word-chars", "");
    public static final StringOption NO_WORD_BEFORE = new StringOption("no-word-before", "");
    public static final StringOption NO_WORD_AFTER = new StringOption("no-word-after", "");
    public static final EnumOption<Balancing> BALANCING = new EnumOption<Balancing>("balancing", Balancing.OUTER);

    public static NEROptions from(Map<String, Object> options) {
      NEROptions nerOptions = new NEROptions();
      for (Entry<String, Object> entry : options.entrySet()) {
        Option<?> option = nerOptions.option(entry.getKey());
        Object value = entry.getValue();
        if (option != null) {
          if (option instanceof StringOption && value instanceof String) {
            nerOptions.set((StringOption) option, (String) value);
          } else if (option instanceof NumberOption) {
            try {
              nerOptions.set((NumberOption) option, ((Long)value).intValue());
            } catch (ClassCastException e) {
              throw new IllegalArgumentException("The numeric option '"+entry.getKey()+"' cannot be set to '"+value.toString()+"'.");
            }
          } else if (option instanceof EnumOption<?>) {
            nerOptions.set((EnumOption<Balancing>) option, Balancing.parseBalancing((String) value));
          } else {
            throw new IllegalArgumentException("The option '"+entry.getKey()+"' cannot be set to '"+value.toString()+"'.");
          }
        } else {
          throw new IllegalArgumentException("The option '"+entry.getKey()+"' is not valid.");
        }
      }
      return nerOptions;
    }
  }

}
