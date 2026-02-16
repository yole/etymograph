import {WordRefViewModel, WordViewModel} from "@/models";

export const Urls = {
    Words: {
        fromWordForm: function(graph: string, word: WordViewModel){
            if (word.urlKey) {
                return `/${graph}/word/${word.language}/${word.urlKey}/${word.id}`
            }
            return `/${graph}/word/${word.language}/${word.text}`
        },
        fromRef: function(graph: string, word: WordRefViewModel){
            let linkTarget = `/${graph}/word/${word.language}/${word.urlKey ?? word.text.toLowerCase()}`
            if (word.homonym) {
                linkTarget += `/${word.id}`
            }
            return linkTarget
        }
    },
    Rules: {
        phono: (graph: string, lang: string) => `/${graph}/rules/${lang}/phono`
    },
    Phonemes: {
        newPhoneme: (graph: string, lang: string) => `/${graph}/phonemes/${lang}/new`
    }
}
