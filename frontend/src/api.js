function postToBackend(endpoint, data) {
    return fetch(process.env.REACT_APP_BACKEND_URL + endpoint, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(data)
    })
}

export function addWord(lang, text, gloss, source) {
    return postToBackend('word/' + lang, {text: text, gloss: gloss, source: source})
}
