# Etymograph

Etymograph is a database for encoding linguistic information:

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
in practice. The ultimate end goal is to build a system that can become a common reference for all the research
in historical linguistics that is currently scattered across books and papers, so that a researcher could easily find
all the relevant information and references for any linguistic phenomenon they're investigating. This is of course
extremely ambitious, so I'm starting small and building something useful for me personally, but I still have this 
ultimate end goal in mind.

The project includes two databases:
- jrrt.json describes Tolkien's Elven languages. It includes ~70-80% of the Quenya grammar, interlinear glosses for 
  almost all Late Quenya texts and a bunch of etymological information. The contents of this database is heavily
  based on [Eldamo](https://eldamo.org).
- ie.json contains notes from the courses I took in the
 [Leiden Summer School of Languages and Linguistics](https://www.universiteitleiden.nl/en/education/study-programmes/summer-schools/summer-school-in-languages-and-linguistics),
  specifically Old English and Lycian.

## Running the project

To run the project:
- Edit web/src/main/resources/application.properties to specify the database you're going to use;
- Run `./gradlew bootRun` in the project root directory to run the backend;
- Run `npx next` in the `frontend` directory to run the frontend;
- Open `localhost:3000` in the browser to start using Etymograph.

Proper deployment, access control and other features required for actual use by other people will be added at a later
time.
