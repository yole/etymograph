export function allowEdit() {
    return process.env.NEXT_PUBLIC_READONLY !== "true";
}

export async function fetchBackend(url) {
    const res= await fetch(process.env.NEXT_PUBLIC_BACKEND_URL + url, { headers: { 'Accept': 'application/json'} })
    const loaderData = await res.json()
    return {
        props: {
            loaderData
        }
    }
}

export async function fetchAllLanguagePaths() {
    const {props} = await fetchBackend(`language`)
    const paths = props.loaderData.map(lang => ({params: {lang: lang.shortName}}))
    return {paths, fallback: false}
}

function postToBackend(endpoint, data) {
    let url = process.env.NEXT_PUBLIC_BACKEND_URL + endpoint;
    console.log(url)
    return fetch(url, {
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

export function updateWord(id, text, gloss, fullGloss, pos, source, notes) {
    return postToBackend(`word/${id}/update`, {text: text, gloss: gloss, fullGloss: fullGloss, pos: pos, source: source, notes})
}

export function deleteWord(id) {
    return postToBackend(`word/${id}/delete`, {})
}

export function addRule(name, fromLang, toLang, addedCategories, replacedCategories, fromPOS, toPOS, text, source, notes) {
    return postToBackend('rule',
        {name: name, fromLang: fromLang, toLang: toLang, text: text,
            addedCategories: addedCategories,
            replacedCategories: replacedCategories,
            fromPOS: fromPOS,
            toPOS: toPOS,
            source: source,
            notes: notes
        })
}

export function updateRule(id, name, fromLang, toLang, addedCategories, replacedCategories, fromPOS, toPOS, text, source, notes) {
    return postToBackend('rule/' + id, {name: name, fromLang: fromLang, toLang: toLang, text: text,
        addedCategories: addedCategories,
        replacedCategories: replacedCategories,
        fromPOS: fromPOS,
        toPOS: toPOS,
        source: source,
        notes: notes})
}

export function deleteRule(id) {
    return postToBackend(`rule/${id}/delete`, {})
}

export function addCorpusText(lang, title, text, source) {
    return postToBackend(`corpus/${lang}/new`, {title: title, text: text, source: source})
}

export function associateWord(corpusTextId, wordId, index) {
    return postToBackend(`corpus/text/${corpusTextId}/associate`, {wordId: wordId, index: index})
}

export function addLink(fromEntity, toEntity, linkType, ruleNames) {
    return postToBackend('link', {fromEntity: fromEntity, toEntity: toEntity, linkType: linkType, ruleNames: ruleNames})
}

export function addRuleLink(fromEntity, toRuleName, linkType) {
    return postToBackend('link/rule', {fromEntity: fromEntity, toRuleName: toRuleName, linkType: linkType})
}

export function deleteLink(fromWord, toWord, linkType) {
    return postToBackend('link/delete', {fromEntity: fromWord, toEntity: toWord, linkType: linkType})
}

export function updateLink(fromWord, toWord, linkType, ruleNames) {
    return postToBackend('link/update', {fromEntity: fromWord, toEntity: toWord, linkType: linkType, ruleNames: ruleNames})
}

export function addParadigm(name, language, pos, text) {
    return postToBackend('paradigms/' + language, {name: name, pos: pos, text: text})
}

export function updateParadigm(id, name, pos, text) {
    return postToBackend('paradigm/' + id, {name: name, pos: pos, text: text})
}

export function addLanguage(name, shortName) {
    return postToBackend('languages', {name: name, shortName: shortName})
}

export function updateLanguage(lang, letterNorm, digraphs, phonemeClasses, diphthongs, syllableStructures, wordFinals, stressRule) {
    return postToBackend(`language/${lang}`,
        {
            letterNormalization: letterNorm,
            digraphs: digraphs,
            phonemeClasses: phonemeClasses,
            diphthongs: diphthongs,
            syllableStructures: syllableStructures,
            wordFinals: wordFinals,
            stressRuleName: stressRule
        }
    )
}
