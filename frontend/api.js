export function allowEdit() {
    return process.env.NEXT_PUBLIC_READONLY !== "true";
}

export async function fetchBackend(url, withGlobalState) {
    const fullUrl = process.env.NEXT_PUBLIC_BACKEND_URL + url
    const res= await fetch(fullUrl, { headers: { 'Accept': 'application/json'} })
    if (res.status === 404) {
        return {
            notFound: true
        }
    }
    const loaderData = await res.json()
    if (withGlobalState) {
        const allLanguages = await fetch(`${process.env.NEXT_PUBLIC_BACKEND_URL}language`, { headers: { 'Accept': 'application/json'} })
        const allLanguagesJson = await allLanguages.json()
        const allRules = await fetch(`${process.env.NEXT_PUBLIC_BACKEND_URL}rules`, { headers: { 'Accept': 'application/json'} })
        const alllRulesJson = await allRules.json()
        return {
            props: {
                loaderData,
                globalState: {
                    languages: allLanguagesJson,
                    rules: alllRulesJson
                }
            }
        }
    }
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

export async function fetchAlternatives(corpusTextId, index) {
    const fullUrl = process.env.NEXT_PUBLIC_BACKEND_URL + `corpus/text/${corpusTextId}/alternatives/${index}`
    const res= await fetch(fullUrl, { headers: { 'Accept': 'application/json'} })
    return await res.json()
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

export function addWord(lang, text, gloss, fullGloss, posClasses, reconstructed, source, notes) {
    return postToBackend('word/' + lang,
        {text: text, gloss: gloss, fullGloss: fullGloss, posClasses: posClasses, reconstructed, source: source, notes: notes}
    )
}

export function updateWord(id, text, gloss, fullGloss, posClasses, reconstructed, source, notes) {
    return postToBackend(`word/${id}/update`,
        {
            text: text, gloss: gloss, fullGloss: fullGloss, posClasses: posClasses, reconstructed, source: source, notes
        })
}

export function deleteWord(id) {
    return postToBackend(`word/${id}/delete`, {})
}

export function addWordSequence(text, source) {
    return postToBackend(`wordSequence`, {sequence: text, source})
}

export function addRule(data) {
    return postToBackend('rule', data)
}

export function updateRule(id, data) {
    return postToBackend('rule/' + id, data)
}

export function deleteRule(id) {
    return postToBackend(`rule/${id}/delete`, {})
}

export function addRuleSequence(data) {
    return postToBackend('rule/sequence', data)
}

export function updateRuleSequence(id, data) {
    return postToBackend('rule/sequence/' + id, data)
}

export function applyRuleSequence(seqId, fromWordId, toWordId) {
    return postToBackend(`rule/sequence/${seqId}/apply`, {
        linkFromId: fromWordId,
        linkToId: toWordId
    })
}

export function deriveThroughRuleSequence(wordId, seqId) {
    return postToBackend(`word/${wordId}/derive`, {sequenceId: seqId})
}

export function addCorpusText(lang, data) {
    return postToBackend(`corpus/${lang}/new`, data)
}

export function updateCorpusText(id, data) {
    return postToBackend(`corpus/text/${id}`, data)
}

export function addTranslation(corpusTextId, data) {
    return postToBackend('translation', {corpusTextId: corpusTextId, ...data})
}

export function editTranslation(id, data) {
    return postToBackend(`translations/${id}`, data)
}

export function associateWord(corpusTextId, wordId, index) {
    return postToBackend(`corpus/text/${corpusTextId}/associate`, {wordId: wordId, index: index})
}

export function acceptAlternative(corpusTextId, index, wordId, ruleId) {
    return postToBackend(`corpus/text/${corpusTextId}/accept`, {wordId: wordId, ruleId: ruleId, index: index})
}

export function addLink(fromEntity, toEntity, linkType, ruleNames, source, notes) {
    return postToBackend('link', {fromEntity, toEntity, linkType, ruleNames, source, notes})
}

export function addRuleLink(fromEntity, toRuleName, linkType, source) {
    return postToBackend('link/rule',
        {fromEntity: fromEntity, toRuleName: toRuleName, linkType: linkType, source: source}
    )
}

export function deleteLink(fromEntity, toEntity, linkType) {
    return postToBackend('link/delete', {fromEntity: fromEntity, toEntity: toEntity, linkType: linkType})
}

export function updateLink(fromWord, toWord, linkType, ruleNames, source, notes) {
    return postToBackend('link/update', {fromEntity: fromWord, toEntity: toWord, linkType: linkType, ruleNames: ruleNames, source: source, notes: notes})
}

export function createCompound(compoundWord, firstComponentWord, source, notes) {
    return postToBackend('compound', {compoundId: compoundWord, firstComponentId: firstComponentWord, source, notes})
}

export function addToCompound(compoundId, componentWord) {
    return postToBackend(`compound/${compoundId}/add`,{componentId: componentWord})
}

export function deleteCompound(compoundId) {
    return postToBackend(`compound/${compoundId}/delete`)
}

export function addParadigm(name, language, pos, text) {
    return postToBackend('paradigms/' + language, {name: name, pos: pos, text: text})
}

export function updateParadigm(id, name, pos, text) {
    return postToBackend('paradigm/' + id, {name: name, pos: pos, text: text})
}

export function deleteParadigm(id) {
    return postToBackend(`paradigm/${id}/delete`)
}

export function updateWordParadigm(id, paradigm) {
    return postToBackend(`word/${id}/paradigm`, {items: [...paradigm]})
}

export function addLanguage(name, shortName, reconstructed) {
    return postToBackend('languages', {name: name, shortName: shortName, reconstructed: reconstructed})
}

export function updateLanguage(
    lang, diphthongs, syllableStructures, stressRule, phonotacticsRule, orthographyRule,
    grammaticalCategories, wordClasses
) {
    return postToBackend(`language/${lang}`,
        {
            diphthongs: diphthongs,
            syllableStructures: syllableStructures,
            stressRuleName: stressRule,
            phonotacticsRuleName: phonotacticsRule,
            orthographyRuleName: orthographyRule,
            grammaticalCategories: grammaticalCategories,
            wordClasses: wordClasses
        }
    )
}

export function addPhoneme(lang, graphemes, sound, classes, historical, source) {
    return postToBackend(`phonemes/${lang}`, {
        graphemes: graphemes,
        sound: sound,
        classes: classes,
        historical: historical,
        source: source
    })
}

export function updatePhoneme(id, graphemes, sound, classes, historical, source) {
    return postToBackend(`phoneme/${id}`, {
        graphemes: graphemes,
        sound: sound,
        classes: classes,
        historical: historical,
        source: source
    })
}

export function deletePhoneme(id) {
    return postToBackend(`phoneme/${id}/delete`)
}

export function copyPhonemes(toLang, fromLang) {
    return postToBackend(`language/${toLang}/copyPhonemes`, {fromLang: fromLang})
}

export function addPublication(data) {
    return postToBackend('publications', data)
}

export function updatePublication(id, data) {
    return postToBackend(`publication/${id}`, data)
}
