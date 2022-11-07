function postToBackend(endpoint, data) {
    return fetch(process.env.REACT_APP_BACKEND_URL + endpoint, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(data)
    })
}

export function addWord(lang, text, gloss, pos, source, notes) {
    return postToBackend('word/' + lang, {text: text, gloss: gloss, pos: pos, source: source, notes: notes})
}

export function updateWord(id, gloss, pos, source, notes) {
    return postToBackend(`word/${id}/update`, {gloss: gloss, pos: pos, source: source, notes})
}

export function deleteWord(id) {
    return postToBackend(`word/${id}/delete`, {})
}

export function addRule(fromLang, toLang, addedCategories, text, source) {
    return postToBackend('rule',
        {fromLang: fromLang, toLang: toLang, text: text, addedCategories: addedCategories, source: source})
}

export function updateRule(id, fromLang, toLang, text) {
    return postToBackend('rule/' + id, {fromLang: fromLang, toLang: toLang, text: text})
}

export function addCorpusText(text) {
    return postToBackend("corpus", {text: text})
}

export function addLink(fromWord, toWord, linkType) {
    return postToBackend('link', {fromWord: fromWord, toWord: toWord, linkType: linkType})
}

export function deleteLink(fromWord, toWord, linkType) {
    return postToBackend('link/delete', {fromWord: fromWord, toWord: toWord, linkType: linkType})
}
