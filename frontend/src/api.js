function postToBackend(endpoint, data) {
    return fetch(process.env.REACT_APP_BACKEND_URL + endpoint, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(data)
    })
}

export function addWord(lang, text, gloss, fullGloss, pos, source, notes) {
    return postToBackend('word/' + lang, {text: text, gloss: gloss, fullGloss: fullGloss, pos: pos, source: source, notes: notes})
}

export function updateWord(id, gloss, fullGloss, pos, source, notes) {
    return postToBackend(`word/${id}/update`, {gloss: gloss, fullGloss: fullGloss, pos: pos, source: source, notes})
}

export function deleteWord(id) {
    return postToBackend(`word/${id}/delete`, {})
}

export function addRule(name, fromLang, toLang, addedCategories, replacedCategories, text, source, notes) {
    return postToBackend('rule',
        {name: name, fromLang: fromLang, toLang: toLang, text: text,
            addedCategories: addedCategories,
            replacedCategories: replacedCategories,
            source: source,
            notes: notes
        })
}

export function updateRule(id, name, fromLang, toLang, addedCategories, replacedCategories, text, source, notes) {
    return postToBackend('rule/' + id, {name: name, fromLang: fromLang, toLang: toLang, text: text,
        addedCategories: addedCategories,
        replacedCategories: replacedCategories,
        source: source,
        notes: notes})
}

export function addCorpusText(lang, text) {
    return postToBackend(`corpus/${lang}/new`, {text: text})
}

export function associateWord(corpusTextId, wordId) {
    return postToBackend(`corpus/text/${corpusTextId}/associate`, {wordId: wordId})
}

export function addLink(fromWord, toWord, linkType, ruleNames) {
    return postToBackend('link', {fromWord: fromWord, toWord: toWord, linkType: linkType, ruleNames: ruleNames})
}

export function deleteLink(fromWord, toWord, linkType) {
    return postToBackend('link/delete', {fromWord: fromWord, toWord: toWord, linkType: linkType})
}

export function updateLink(fromWord, toWord, linkType, ruleNames) {
    return postToBackend('link/update', {fromWord: fromWord, toWord: toWord, linkType: linkType, ruleNames: ruleNames})
}

export function addParadigm(name, language, pos, text) {
    return postToBackend('paradigms/' + language, {name: name, pos: pos, text: text})
}

export function updateParadigm(id, name, pos, text) {
    return postToBackend('paradigm/' + id, {name: name, pos: pos, text: text})
}

export function updateLanguage(lang, letterNorm) {
    return postToBackend('language/' + lang, {letterNormalization: letterNorm})
}
