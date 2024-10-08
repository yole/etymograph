export function allowEdit() {
    return process.env.NEXT_PUBLIC_READONLY !== "true";
}

export async function fetchBackend(graph, url, withGlobalState) {
    const fullUrl = `${process.env.NEXT_PUBLIC_BACKEND_URL}${graph}/${url}`
    const res = await fetch(fullUrl, { headers: { 'Accept': 'application/json'} })
    if (res.status === 404) {
        return {
            notFound: true
        }
    }
    const loaderData = await res.json()
    if (withGlobalState) {
        const allGraphs = await fetch(`${process.env.NEXT_PUBLIC_BACKEND_URL}graphs`, { headers: { 'Accept': 'application/json'} })
        const allGraphsJson = await allGraphs.json()
        const allLanguages = await fetch(`${process.env.NEXT_PUBLIC_BACKEND_URL}${graph}/language`, { headers: { 'Accept': 'application/json'} })
        const allLanguagesJson = await allLanguages.json()
        const allRules = await fetch(`${process.env.NEXT_PUBLIC_BACKEND_URL}${graph}/rules`, { headers: { 'Accept': 'application/json'} })
        const alllRulesJson = await allRules.json()
        const allInputAssists = await fetch(`${process.env.NEXT_PUBLIC_BACKEND_URL}${graph}/inputAssist`, { headers: { 'Accept': 'application/json'} })
        const allInputAssistsJson = await allInputAssists.json()
        return {
            props: {
                loaderData,
                globalState: {
                    graphs: allGraphsJson,
                    languages: allLanguagesJson,
                    rules: alllRulesJson,
                    inputAssists: allInputAssistsJson
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

export async function fetchGraphs() {
    const response = await fetch(`${process.env.NEXT_PUBLIC_BACKEND_URL}graphs`, { headers: { 'Accept': 'application/json'} })
    const loaderData = await response.json()
    return {props: {loaderData}}
}

export async function fetchAllGraphs() {
    const response = await fetch(`${process.env.NEXT_PUBLIC_BACKEND_URL}graphs`, { headers: { 'Accept': 'application/json'} })
    const graphs = await response.json()
    const paths = graphs.map(graph => ({params: {graph: graph.id}}))
    return {paths, fallback: false}
}

export async function fetchAllLanguagePaths() {
    return fetchPathsForAllGraphs("language", (p) => ({lang: p.shortName}))
}

export async function fetchPathsForAllGraphs(url, callback) {
    const graphs = await fetchAllGraphs()
    const langPaths = await Promise.all(graphs.paths.map(async (g) => {
        const {props} = await fetchBackend(g.params.graph, url)
        return props.loaderData.map((l) => ({ graph: g.params.graph, ...l }))
    }))
    const paths = langPaths.flat().map(p => {
        const data = callback(p)
        return ({params: {graph: p.graph, ...data}});
    })
    return {paths, fallback: allowEdit()}
}

export async function fetchAlternatives(graph, corpusTextId, index) {
    const fullUrl = process.env.NEXT_PUBLIC_BACKEND_URL + `${graph}/corpus/text/${corpusTextId}/alternatives/${index}`
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

export function addWord(graph, lang, text, gloss, fullGloss, pos, classes, reconstructed, source, notes) {
    return postToBackend(`${graph}/word/${lang}`,
        {text: text, gloss: gloss, fullGloss: fullGloss, pos, classes, reconstructed, source: source, notes: notes}
    )
}

export function updateWord(graph, id, text, gloss, fullGloss, pos, classes, reconstructed, source, notes) {
    return postToBackend(`${graph}/word/${id}/update`,
        {
            text: text, gloss: gloss, fullGloss: fullGloss, pos, classes, reconstructed, source: source, notes
        })
}

export function lookupWord(graph, id, data) {
    return postToBackend(`${graph}/word/${id}/lookup`, data)
}

export function suggestParseCandidates(graph, id) {
    return postToBackend(`${graph}/word/${id}/parse`)
}

export function suggestCompound(graph, id, compoundId) {
    return postToBackend(`${graph}/word/${id}/suggestCompound`, {compoundId})
}

export function deleteWord(graph, id) {
    return postToBackend(`${graph}/word/${id}/delete`, {})
}

export function addWordSequence(graph, text, source) {
    return postToBackend(`${graph}/wordSequence`, {sequence: text, source})
}

export function addRule(graph, data) {
    return postToBackend(`${graph}/rule`, data)
}

export function updateRule(graph, id, data) {
    return postToBackend(`${graph}/rule/${id}`, data)
}

export function deleteRule(graph, id) {
    return postToBackend(`${graph}/rule/${id}/delete`, {})
}

export function addRuleSequence(graph, data) {
    return postToBackend(`${graph}/rule/sequence`, data)
}

export function updateRuleSequence(graph, id, data) {
    return postToBackend(`${graph}/rule/sequence/${id}`, data)
}

export function applyRuleSequence(graph, seqId, fromWordId, toWordId) {
    return postToBackend(`${graph}/rule/sequence/${seqId}/apply`, {
        linkFromId: fromWordId,
        linkToId: toWordId
    })
}

export function traceRule(graph, ruleId, word, reverse) {
    return postToBackend(`${graph}/rule/${ruleId}/trace`, {word, reverse})
}

export function deriveThroughRuleSequence(graph, wordId, seqId) {
    return postToBackend(`${graph}/word/${wordId}/derive`, {sequenceId: seqId})
}

export function addCorpusText(graph, lang, data) {
    return postToBackend(`${graph}/corpus/${lang}/new`, data)
}

export function updateCorpusText(graph, id, data) {
    return postToBackend(`${graph}/corpus/text/${id}`, data)
}

export function addTranslation(graph, corpusTextId, data) {
    return postToBackend(`${graph}/translation`, {corpusTextId: corpusTextId, ...data})
}

export function editTranslation(graph, id, data) {
    return postToBackend(`${graph}/translations/${id}`, data)
}

export function associateWord(graph, corpusTextId, wordId, index, contextGloss) {
    return postToBackend(`${graph}/corpus/text/${corpusTextId}/associate`, {wordId, index, contextGloss})
}

export function acceptAlternative(graph, corpusTextId, index, wordId, ruleId) {
    return postToBackend(`${graph}/corpus/text/${corpusTextId}/accept`, {wordId: wordId, ruleId: ruleId, index: index})
}

export function addLink(graph, fromEntity, toEntity, linkType, ruleNames, source, notes) {
    return postToBackend(`${graph}/link`, {fromEntity, toEntity, linkType, ruleNames, source, notes})
}

export function addRuleLink(graph, fromEntity, toRuleName, linkType, source, notes) {
    return postToBackend(`${graph}/link/rule`,
        {fromEntity, toRuleName, linkType, source, notes}
    )
}

export function deleteLink(graph, fromEntity, toEntity, linkType) {
    return postToBackend(`${graph}/link/delete`, {fromEntity: fromEntity, toEntity: toEntity, linkType: linkType})
}

export function updateLink(graph, fromWord, toWord, linkType, ruleNames, source, notes) {
    return postToBackend(`${graph}/link/update`, {fromEntity: fromWord, toEntity: toWord, linkType: linkType, ruleNames: ruleNames, source: source, notes: notes})
}

export function createCompound(graph, compoundWord, firstComponentWord, source, notes) {
    return postToBackend(`${graph}/compound`, {compoundId: compoundWord, firstComponentId: firstComponentWord, source, notes})
}

export function addToCompound(graph, compoundId, componentWord, markHead) {
    return postToBackend(`${graph}/compound/${compoundId}/add`,{componentId: componentWord, markHead})
}

export function updateCompound(graph, compoundId, source, notes, head) {
    return postToBackend(`${graph}/compound/${compoundId}`, {source, notes, head})
}

export function deleteCompound(graph, compoundId) {
    return postToBackend(`${graph}/compound/${compoundId}/delete`)
}

export function addParadigm(graph, name, language, pos, text) {
    return postToBackend(`${graph}/paradigms/${language}`, {name: name, pos: pos, text: text})
}

export function updateParadigm(graph, id, name, pos, text) {
    return postToBackend(`${graph}/paradigm/${id}`, {name: name, pos: pos, text: text})
}

export function deleteParadigm(graph, id) {
    return postToBackend(`${graph}/paradigm/${id}/delete`)
}

export function updateWordParadigm(graph, id, paradigm) {
    return postToBackend(`${graph}/word/${id}/paradigm`, {items: [...paradigm]})
}

export function generateParadigm(graph, lang, data) {
    return postToBackend(`${graph}/paradigm/generate`, {lang: lang, ...data})
}

export function addLanguage(graph, name, shortName, reconstructed) {
    return postToBackend(`${graph}/languages`, {name: name, shortName: shortName, reconstructed: reconstructed})
}

export function updateLanguage(graph, lang, data) {
    return postToBackend(`${graph}/language/${lang}`, data)
}

export function addPhoneme(graph, lang, data) {
    return postToBackend(`${graph}/phonemes/${lang}`, data)
}

export function updatePhoneme(graph, id, data) {
    return postToBackend(`${graph}/phoneme/${id}`, data)
}

export function deletePhoneme(graph, id) {
    return postToBackend(`${graph}/phoneme/${id}/delete`)
}

export function copyPhonemes(graph, toLang, fromLang) {
    return postToBackend(`${graph}/language/${toLang}/copyPhonemes`, {fromLang: fromLang})
}

export function addPublication(graph, data) {
    return postToBackend(`${graph}/publications`, data)
}

export function updatePublication(graph, id, data) {
    return postToBackend(`${graph}/publication/${id}`, data)
}

export async function callApiAndRefresh(apiCall, router, setErrorText){
    const result = await apiCall()
    if (result.status === 200) {
        router.replace(router.asPath)
    }
    else {
        const jr = await r.json()
        setErrorText(jr.message)
    }
}
