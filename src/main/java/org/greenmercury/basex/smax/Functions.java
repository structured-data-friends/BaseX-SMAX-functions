package org.greenmercury.basex.smax;

import static org.basex.query.value.type.SeqType.NODE_O;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;

import org.basex.api.dom.BXElem;
import org.basex.query.CompileContext;
import org.basex.query.QueryContext;
import org.basex.query.QueryException;
import org.basex.query.QueryModule;
import org.basex.query.QueryString;
import org.basex.query.expr.Arr;
import org.basex.query.expr.Expr;
import org.basex.query.util.list.AnnList;
import org.basex.query.value.item.FuncItem;
import org.basex.query.value.item.QNm;
import org.basex.query.value.node.ANode;
import org.basex.query.value.node.FElem;
import org.basex.query.value.type.FuncType;
import org.basex.query.value.type.NodeType;
import org.basex.query.var.Var;
import org.basex.query.var.VarRef;
import org.basex.query.var.VarScope;
import org.basex.util.InputInfo;
import org.basex.util.hash.IntObjMap;
import org.basex.util.hash.TokenMap;
import org.greenmercury.basex.smax.NamedEntityRecognition.NEROptions;
import org.greenmercury.smax.SmaxDocument;
import org.greenmercury.smax.SmaxException;
import org.greenmercury.smax.convert.DomElement;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class Functions extends QueryModule
{

  public Functions()
  {
  }

  /**
   * Recognize named entities in the text within a node.
   * named-entity-recognition($grammar as item(), $options as map(*)?)
   *   as  function(node()) as node()
   */
  @Requires(Permission.NONE)
  @Deterministic
  @ContextDependent
  public FuncItem namedEntityRecognition(Object grammar, Node matchElementTemplate, HashMap<String, Object> options) throws QueryException {
    NamedEntityRecognition ner = null;
    if (grammar instanceof URL) {
      ner = new NamedEntityRecognition((URL)grammar, matchElementTemplate, NEROptions.from(options));
    } else if (grammar instanceof URI) {
      try {
        ner = new NamedEntityRecognition(((URI)grammar).toURL(), matchElementTemplate, NEROptions.from(options));
      } catch (MalformedURLException e) {
        throw new QueryException(e);
      }
    } else if (grammar instanceof ANode) {
      ner = new NamedEntityRecognition(new String(((ANode)grammar).string()), matchElementTemplate, NEROptions.from(options));
    } else if (grammar instanceof String) {
      ner = new NamedEntityRecognition((String)grammar, matchElementTemplate, NEROptions.from(options));
    } else {
      throw new QueryException("The first parameter ($grammar) of named-entity-recognition can not be a "+grammar.getClass().getName());
    }
    // The following line uses queryContext. Can we do without it?
    final Var[] params = { new VarScope().addNew(new QNm("input"), NODE_O, true, queryContext, null) }; // Types of the arguments of the generated function.
    final Expr arg = new VarRef(null, params[0]);
    NamedEntityRecognitionFunction nerf = new NamedEntityRecognitionFunction(null, ner, arg);
    final FuncType ft = FuncType.get(nerf.seqType(), NODE_O); // Type of the generated function.
    return new FuncItem(null, nerf, params, AnnList.EMPTY, ft, params.length, null);
  }

  private static final class NamedEntityRecognitionFunction extends Arr {

    private final NamedEntityRecognition ner;

    protected NamedEntityRecognitionFunction(InputInfo info, NamedEntityRecognition ner, final Expr... args)
    {
      super(info, NODE_O, args);
      this.ner = ner;
    }

    @Override
    public ANode item(final QueryContext qc, final InputInfo ii)
    throws QueryException
    {
      final ANode inputNode = toNode(arg(0), qc);
      if (inputNode.type == NodeType.ELEMENT) {
        BXElem bxElement = (BXElem) inputNode.toJava();
        SmaxDocument smaxDocument;
        try {
          smaxDocument = DomElement.toSmax(bxElement);
        } catch (SmaxException e) {
          throw new QueryException(e);
        }
        // Apply the NER to the SMAX document.
        this.ner.scan(smaxDocument);
        try {
          Element resultElement = DomElement.documentFromSmax(smaxDocument).getDocumentElement();
          return FElem.build(resultElement, new TokenMap()).finish();
        } catch (Exception e) {
          throw new QueryException(e);
        }
      } else {
        //final BXNode bxNode = inputNode.toJava();
        //TODO: Handle text nodes.
        throw new QueryException("Named Entity Recognition currently only works on elements.");
      }
    }

    @Override
    public Expr copy(final CompileContext cc, final IntObjMap<Var> vm) {
      return copyType(new NamedEntityRecognitionFunction(info, ner, copyAll(cc, vm, args())));
    }

    @Override
    public void toString(final QueryString qs) {
      qs.token("named-entity-recognition").params(exprs);
    }
  }


/*
    if (node == null || node.type != NodeType.ELEMENT) {
      throw new QueryException("The argument must be an element.");
    }
    BXElem bxElement = (BXElem) node.toJava();
    SmaxDocument smaxDocument = DomElement.toSmax(bxElement);
    try
    {
      return DomElement.documentFromSmax(smaxDocument);
    }
    catch (Exception e)
    {
      throw new QueryException(e);
    }


  public FuncItem recognizer(final QueryContext qc) {
    InputInfo info = null;
    final Var[] params = { new VarScope().addNew(new QNm("input"), ELEMENT_O, true, qc, info)}; // Type of the argument of the generated parse function
    final Expr arg = new VarRef(info, params[0]);
    final NERFunction nerFunction = new NERFunction(null, null, arg);
    final FuncType ft = FuncType.get(nerFunction.seqType(), ELEMENT_O);
    return new FuncItem(info, nerFunction, params, AnnList.EMPTY, ft, params.length, null);
  }

  private static final class NERFunction extends Arr {

    private final TrieNER trieNER;

    protected NERFunction(final InputInfo info, final TrieNER trieNER, final Expr... args) {
      super(info, DOCUMENT_NODE_O, args);
      this.trieNER = trieNER;
    }

    @Override
    public Expr copy(final CompileContext cc, final IntObjMap<Var> vm) {
      return copyType(new NERFunction(info, trieNER, copyAll(cc, vm, args())));
    }

    @Override
    public void toString(final QueryString qs) {
      qs.token("parse-invisible-xml").params(exprs);
    }
  }
*/

}
