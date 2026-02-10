import {WordViewModel} from "@/models";

export const Urls = {
    Words: {
        fromWordForm: function(graph: string, word: WordViewModel){
            if (word.urlKey) {
                return `/${graph}/word/${word.language}/${word.urlKey}/${word.id}`;
            }
            return `/${graph}/word/${word.language}/${word.text}`;
        }
    },
    Rules: {
        phono: function(graph: string, lang: string){
            return `/${graph}/rules/${lang}/phono`
        }
    }
}
