# Etymograph

Etymograph is a DSL for expressing morphological and phonological rules, along with a linguistic
database allowing to apply the rules to real language material. The Etymograph data model allows to express:

- Languages in a language family;
- Words and relationships between them (derivation within a language and etymological relationships across languages);
- Grammar rules (and exceptions to those rules);
- Sound correspondences;
- Texts with interlinear glosses.

The database is intended to be able to represent ambiguity in all aspects of the stored data. For example, if different
authors propose different etymologies for a word or different versions of a sound correspondence rule, all of these
versions can be entered into the database, with links to the respective sources and notes on the ambiguity.

Possible uses for Etymograph include:
- Actual linguistic research (hopefully at some point);
- Taking notes in a language class;
- Creating a conlang.

The project is at an early prototype stage. Both the data model for encoding the information and the actual
implementation are still not much more than toys, but I'm trying to evolve them to something actually useful
in practice. The ideal end goal is to build a system that can become a common reference for all the research
in historical linguistics that is currently scattered across books and papers, so that a researcher could easily find
all the relevant information and references for any linguistic phenomenon they're investigating. This is of course
extremely ambitious, so I'm starting small and building something useful for me personally, but I still have this 
ultimate end goal in mind.

The project includes two databases:
- ie.json contains notes from the courses I took in the
  [Leiden Summer School of Languages and Linguistics](https://www.universiteitleiden.nl/en/education/study-programmes/summer-schools/summer-school-in-languages-and-linguistics),
  specifically Old English and Lycian, as well as notes from my study of Old Norse.
- jrrt.json describes Tolkien's Elvish languages. It includes ~70-80% of the Quenya grammar, interlinear glosses for 
  almost all Late Quenya texts and a bunch of etymological information. The contents of this database is heavily
  based on [Eldamo](https://eldamo.org).

## Accessing the Databases

Read-only versions of the databases are now available online. (When you run Etymograph locally, you can also edit the
databases; changes are then saved to the .json files on your machine.)

- Elvish database: https://elvish-etymograph.yole.page/
- Indo-European database: https://etymograph.yole.page/

## Running the project

To run the project:
- Edit web/src/main/resources/application.properties to specify the database you're going to use;
- Run `./gradlew bootRun` in the project root directory to run the backend;
- Run `npx next` in the `frontend` directory to run the frontend;
- Open `localhost:3000` in the browser to start using Etymograph.

Proper deployment, access control and other features required for actual use by other people will be added at a later
time.

## Data Model and DSL Documentation

All linguistic entities in the Etymograph database have the following two common properties:

  * Source: Bibliographic reference(s) to where the word, rule etc. is described or analyzed
  * Notes: Freeform notes, allowing to specify arbitrary addiional information.

### Words

The Etymograph database contains both dictionary forms of words and derived forms which are attested in corpus
texts. For each word, the following information can be specified:

 * Part of speech (POS)
 * Classes (e.g. gender, declension type, stem type etc.) Possible word classes for each part of speech
   are specified as part of a language definition.
 * Gloss (English translation of the word). You can specify a short gloss (used in interlinear glosses of corpus texts) 
   and a full gloss (which can list multiple meanings or translations). For inflected forms, the gloss is generally
   not stored in the database, but rather calculated from the gloss of the base word.

### Links

Etymograph supports the following types of links between words:

 * Derived: Expresses both morphological inflection and derivation (within a language) and etymological 
   derivation (between languages). For a derived link, you can specify the set of rules which can be applied
   to the base word to arrive to the inflected or derived word.
 * Variation: Expresses spelling variations. A variation of a word does not carry any morphological
   or semantic information of its own; all the information is inherited from the base word.
 * Compound: A word can be defined as a compound of other words (components). A word can be broken into 
   components in several different ways, if the correct analysis is unknown and there are multiple 
   hypotheses. Right now there is no way to specify the rules for how words change during compounding.
 * Related: Specifies an arbitrary notion of relatedness. A "related" link can also be created between
   a word and a rule, for example, if the rule arose from a grammaticalization of an independent word.
    
### Corpus

Etymograph allows you to store a corpus of texts for the language you're working with. Each word in the
text can be analyzed by creating a "Word" entity for the word and specifying either its meaning or its
derivation from a base word. When creating a new word, Etymograph suggests possible analyses of the word
as an inflected form based on the rules defined for the language. If there are multiple homonymous words 
or forms, you can specify which one is used in the given context.

### Phonemes

The language definition specifies the list of phonemes that exist in a language. Etymograph assumes
a mostly phonemic writing system, where every phoneme is expressed either by a single character (for example,
`a`) or a di- or trigraph (for example, `sh` or `eau`). Context-dependent rules for mapping graphemes to 
phonemes are currently not supported.

For every phoneme, a set of classes (features) is specified: whether it's a vowel or a consonant, and
how it's articulated (back/front, open/closed for vowels, voiced/voiceless, labial/velar etc. for consonants).
The classes can be referenced in rules.

### Rules

Etymograph supports two types of rules: morphological (operating on the word as a whole) and pholological
(operating on individual phonemes). Both types of rules have the same general structure and are defined
in the same UI, but the set of available conditions and instructions is different.

The following general attributes are specified for a rule:

 * Name: Used to refer to the rule in links, paradigms or other rules.
 * From language: The language to which the rule applies.
 * To language: If the rule describes a sound correspondence between languages, this is the resulting
   language.
 * From POS: The part of speech to which the rule applies (optional).
 * To POS: For derivation rules, the POS of the derived word.
 * Added categories: The set of grammatical category values (for example, a specific case, number or person)
   that is expressed by the morphemes added by the rule. Possible grammatical categories and their values
   for each part of speech are specified as part of a language definition. 
 * Removed categories: The set of replaced grammatical category values (for example, a noun inflection
   rule can remove the "nominative case" category value and add "genitive case" instead)

The main body of a rule consists of *conditions* and *instructions*. Each condition or instruction is written
on a separate line. Condition lines end with a colon (`:`), and instruction lines start with a dash (`-`). 
Here's an example (the rule for putting a Quenya noun into the genitive case):

```
 - apply rule 'q-noun-stem'
 
word ends with 'a':
 - change ending to 'o'

word ends with 'o':
 - no change

otherwise:
 - append 'o'
```

When applying a rule to a word, Etymograph starts with executing the instructions before the first condition 
(the "pre-instructions"). Then, it looks for a condition matching the word. If it finds one, it executes the 
corresponding instructions and completes the rule execution (it doesn't look for other matching conditions). 
If none of the conditions match, the result of applying the rule is unknown. (The `otherwise` condition, 
shown in the example above, matches all words not matched by any previous conditions.)

In addition to simple conditions shown above, Etymograph supports compound conditions, which consist of multiple
simple conditions combined with `and` and `or` operators (parentheses can be used to control the precedence).
Here's an example of a compound condition:

```
word is strong and (word ends with 'v' or word ends with 'j'):
```

Conditions with arguments can be negated. This is done by adding a `not` operator before the argument of a condition,
for example, `word ends with not 'a'` or `word is not m`.

#### Morphological (Inflectional and Derivational) Rules

The following conditions can be used in morphological rules:

 * `word ends with`: Checks whether the word ends with the specified sequence of phonemes
   (for example, `word ends with 'ie'`) or a phoneme of a specified class (for example, `word ends with a vowel`).
 * `base word in <language> ends with`: Checks whether the word in the given language from which the current word
   is derived ends with the specified sequence of phonemes (for example, `base word in CE ends with 'ie'`)
   or a phoneme of a specified class (for example, `base word in CE ends with a vowel`).
 * `word begins with`: Checks whether the word begins with the specified sequence of phonemes
   (for example, `word begins with 'm'`) or a phoneme of a specified class (for example, `word begins with a consonant`).
 * `word is`: Checks whether the word belongs to the given class (for example, `word is m`, where `m` is defined
   as a possible value for the 'gender' class of nouns).
 * `number of syllables is `: Checks whether the word has exactly the specified number of syllables
   (for example, `number of syllables is 2`) 
 * `number of syllables is <at least|at most>`: Checks whether the word has at least or at most the specified number 
  of syllables (for example, `number of syllables is at least 3`) 
 * `<ordinal> syllable contains`: Checks if the syllable with the specified ordinal number contains a given
   substring or a phoneme of a given class. Example: `first syllable contains long vowel`
 * `<ordinal> syllable ends with`: Checks if the syllable with the specified ordinal number ends with a given
   substring or a phoneme of a given class. Example: `last syllable ends with voiceless consonant`
 * `<ordinal> <phoneme class> is:` Checks whether the phoneme at given index matches the given phoneme or phoneme class.
   Example: `second to last sound is vowel`.
 * `<ordinal> <phoneme class> of base word in <language> is:` Checks whether the phoneme at given index of word in the 
   given language from which the current word is derived matches the given phoneme or phoneme class.
   Example: `first sound of base word in CE is 'm'`.
 * `stress is`: Checks whether the stress is on the given syllable (for example, `stress is on second to last syllable`)

The following instructions can be used in morphological rules:

 * `no change`: Keep the word unchanged.
 * `append`: Append a suffix to the word. The suffix can either be specified as a literal string enclosed
   in single quotes (for example, `append 'a'`) or as a reference to a phoneme in the word
   (for example, `append last vowel`).
 * `prepend`: Prepend a prefix to the word. Argument is specified in the same way as with `append`.
 * `change ending to`: Change the part of the word matched by the `word ends with` condition in the preceding
   condition line to the specified ending (always specified as a literal string in single quotes).
 * `insert '<sound>' <before|after> <position>`: Inserts characters into the word at the given position.
   Example: `insert 'i' before last consonant'`
 * `apply rule`: Apply the specified morphological or phonological rule to the word. The name of the rule to apply
   is specified in single quotes: `apply rule 'q-noun-stem'`. If there exists a derived word using that
   rule in the derivation link, the text of that word is taken instead of evaluating the conditions
   and instructions of the specified rule (this allows to express partially irregular inflections or derivations).
   If a phonological rule is specified as an argument, it's applied to every phoneme in the word.
 * `apply sound rule`: Apply the specified phonological rule to the specified phoneme (for example,
    `apply sound rule 'on-a-fronting' to first vowel`).
 * `mark word as`: Adds the specified class to the word. This is normally used only in rules which
   are applied through the `apply rule` instruction in pre-instructions; only this usage allows the
   added class to be matched by subsequently executed conditions. (For example: `mark word as m`) 

Phoneme references used in the instructions described above consist of an ordinal number (`first`,
`second`, `third` counting from the beginning of the word, `last`, `second to last`, `third to last`
counting backwards from the end of the word) and a phoneme class (for example, `vowel`). Multiple
phoneme classes can be combined (for example, `voiceless stop`).

#### Phonological Rules

A phonological rule is applied to every phoneme in the word, in order. If a phoneme is matched, the execution
continues. For example, the following rule will change all vowels in the word to 'e':

```
sound is a vowel:
- new sound is 'e'
```

The following conditions can be used in phonological rules:

 * `sound is`: Checks whether the current phoneme matches the given phoneme (enclosed in single quotes;
   for example, `sound is 'e'`) or phoneme class.
 * `<next|second next|previous> sound is`: Checks whether the next or previous sound matches the given phoneme or
    phoneme class. Example: `next sound is 'a'`
 * `<next|second next|previous|second previous> <phoneme class> is:` Checks whether the next or previous phoneme of a given phoneme 
   class matches the given phoneme or phoneme class. Examples: `next stop is voiceless`, `next vowel is 'a'`.
 * `beginning of word`: Checks whether the current phoneme is the first one in the word.
 * `end of word`: Checks whether the current phoneme is the last one in the word.
 * `sound is stressed`: Checks whether the current phoneme is the stressed vowel.
 * `syllable is <ordinal>`: Checks if the current phoneme is in the syllable with the given indes.
   Example: `syllable is second to last` 
 * `sound is same as`: Checks whether the current phoneme is the same as the specified phoneme.
   Example: `sound is same as next vowel`

The following instructions can be used in phonological rules:

 * `no change`: Leaves the phoneme as is.
 * `new sound is`: Replaces the phoneme with another phoneme (or several phonemes), specified in single quotes.
 * `new next sound is`: Replaces the next phoneme with another phoneme (or several phonemes), specified in single quotes.
 * `sound disappears`: Deletes the phoneme from the word.
 * `<next|previous|second next|second previous> sound disappears`: Deletes the next/previous phoneme from the word.
 * `sound is geminated`: Inserts a copy of the current phoneme at the current location. 
 * `<old class> becomes <new class>`: Replaces the phoneme with a different phoneme which has the same set
   of classes, except that it nas <new class> instead of <old class>. Example: `voiceless becomes voiced`.  
 * `<next|previous|second next|second previous> <old class> becomes <new class>`: Replaces the next/previous 
   phoneme with a different phoneme which has the same set of classes, except that it nas <new class> instead of 
   <old class>. Example: `previous long becomes short`.
 * `<sound> is inserted before`: Inserts the specified phoneme before the current phoneme.
* `apply sound rule`: Apply the specified phonological rule to the current phoneme (for example,
  `apply sound rule 'on-a-fronting'`), or to the specified phoneme relative to the current one
  (for example, `apply sound rule 'on-a-fronting' to next sound`).


#### Stress Rule

In a language definition, you can specify the reference to a stress rule. The stress rule uses the same
conditions as morphological rules, and it uses just one instruction:

 * `stress is on`: Specifies the stressed syllable (for example, `stress is on second to last syllable`).

Here's an example of a stress rule:

```
number of syllables is 1 or number of syllables is 2:
 - stress is on first syllable

second to last syllable contains a diphthong or second to last syllable contains a long vowel:
 - stress is on second to last syllable

otherwise:
 - stress is on third to last syllable
 ```
 