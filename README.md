# If you are looking for the transparent invisible XML function
please refer to https://github.com/nverwer/basex-tixml-xar .

# BaseX-SMAX-functions

This project contains Java classes that define XQuery functions for use in [BaseX](https://basex.org/).
These functions use the [SMAX](https://github.com/nverwer/SMAX) representation of XML,
which makes it possible to parse text that is distributed across, and nested within XML elements.

This is work in progress. Bugs will be fixed, and new functions will be added sometimes.

# Installation

1. Download and install [BaseX](https://basex.org/).
1. Download the [SMAX](https://github.com/nverwer/SMAX) source code or clone the Git repository.
   Build it with `mvn install`.
   This will copy the artifacts to your local M2 repository, making SMAX available in your IDE.
   It will also put the SMAX jar `target` directory.
1. Download (or clone) The [BaseX-SMAX-functions](https://github.com/structured-data-friends/BaseX-SMAX-functions/) source code.
   Build it with `mvn install`. This will also produce a jar file in the `target` directory.
1. Go to the directory where you installed BaseX.
   Copy the SMAX and BaseX-SMAX-functions jar files into the `lib/custom` directory within the BaseX installation directory.
1. Now you can (re)start BaseX and use the functions.

# Functions

## named-entity-recognition

```
named-entity-recognition($grammar as item(), $options as map(*)?)
  as  function(node()) as node()
```

This function produces a named entity recognizer that inserts markup around named entities specified by a grammar.
The named entity recognizer is a `function(node()) as node()`.
At the moment, it only accepts an `element()` as its parameter, and produces a copy of that element,
with additional markup for recognized named entities.

The `named-entity-recognition` function takes the following parameters:

* grammar The grammar specifying the named entities. This may be a
    * `xs:anyURI` containing the URL where the grammar may be read from;
    * `node()` or `xs:string` containing the grammar.

    The named entities are defined by rules on separate lines of the form:
    ```
    id <- entity 1⇥entity 2 ...
    ```
    where the entities are separated by tab characters, shown as ⇥.
    Entities may contain spaces, but not tab characters, which would be mapped to spaces anyway.
* matchNodeTemplate The template for the XML element that is inserted for each content fragment that matches a named entity.
    This template must have one empty attribute, which will be filled with the id's of the matched named entities, separated by tab characters.
    There can be more than one id if the same entity occurs in different rules in the grammar.
* options A map with options. The following options are recognized:
    * word-chars Characters that may appear in a word (next to letters and digits).
        They are significant for matching. Default is "", so spaces are not included.
    * no-word-before Characters that may not immediately follow a word (next to letters and digits).
        They cannot follow the end of a match. Default is "".
    * no-word-after Characters that may not immediately precede a word (next to letters and digits).
        Matches can only start on a letter or digit, and not after noWordAfter characters. Default is "".
    * case-insensitive-min-length The minimum entity-length for case-insensitive matching.
        Text fragments larger than this will be scanned case-insensitively.
        Set to -1 to always match case-sensitive. Set to 0 to always match case-insensitive.
        Default is -1.
    * fuzzy-min-length The minimum entity-length for fuzzy matching.
        Text fragments larger than this may contain characters that are not word characters.
        This prevents short words with noise to be recognized.
        Set to -1 to match exact. Set to 0 to match very fuzzy.
        Default is -1.
    * balancing The SMAX balancing strategy that is used when an element for a recognized entity is inserted.
        Default is "OUTER".

Entity names are sequences of one or more words.
A word is a sequence of *word characters*.
Word characters are letters and digits and characters in the value of the `word-chars` option.
Words are separated by sequences containing at least one whitespace character and zero or more non-word characters.

The entity itself is represented by an identifier.
An entity may have multiple names, which will all be recognized as the same identifier.

The named entity recognizer maps each character in its input text to a low ASCII equivalent (codes 0x20 - 0x7E).
This makes the recognition insensitive to diacritics, different space characters, and different styles of quotation marks and dashes.
Word-separating sequences of characters will be treated like a single space.
This character mapping is applied to both the grammar input and the text that is scanned for named entities.

Suppose "CF", "RSVP" and "THE" are entity names in the grammar, probably abbreviations that should be recognized.
Setting caseInsensitiveMinLength to 4 prevents the scanner from recognizing "the" in "Do the right thing."
If '.' is not a word character, setting fuzzyMinLength to 4 prevents the scanner from recognizing "C.F" in "C.F. Gauss was a German mathematician."
With these settings, "r.s.v.p" (without trailing dot) would be recognized in "Put an r.s.v.p. at the end."

The following example illustrates this.
```
import module namespace smax = "java:org.greenmercury.basex.smax.Functions";
let $grammar := ``[
cf <- CF
rsvp <- RSVP
the <- THE
]``
let $options := map
{ 'case-insensitive-min-length': 4
, 'fuzzy-min-length': 4
, 'word-chars': ''
}
let $ner := smax:named-entity-recognition($grammar, <ner entity-id=""/>, $options)
let $input :=
<r>
  <p>C.F. Gauss was a German mathematician.</p>
  <p>Put the r.s.v.p. at the end.</p>
</r>
return $ner($input)
```
The output of this XQuery is
```
<r>
  <p>C.F. Gauss was a German mathematician.</p>
  <p>Put the <ner entity-id="rsvp">r.s.v.p</ner>. at the end.</p>
</r>
```
