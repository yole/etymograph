import {AddPublicationParameters, CorpusTextParams, LookupParameters} from "@/models";

export function allowEdit() {
    return process.env.NEXT_PUBLIC_READONLY !== "true";
}

export async function fetchBackend(graph: string, url, withGlobalState = false) {
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
        const {props} = await fetchBackend(g.params.graph, url, false)
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

function postToBackend(endpoint: string, data: any): Promise<Response> {
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

export function addWord(
    graph: string, lang, text,
    gloss?: string,
    fullGloss?: string,
    pos?: string,
    classes?: string,
    reconstructed: boolean = false,
    source: string = null,
    notes: string = null
) {
    return postToBackend(`${graph}/word/${lang}`,
        {text: text, gloss: gloss, fullGloss: fullGloss, pos, classes, reconstructed, source: source, notes: notes}
    )
}

export function updateWord(graph: string, id: number, text, gloss, fullGloss, pos, classes, reconstructed, source, notes) {
    return postToBackend(`${graph}/word/${id}/update`,
        {
            text: text, gloss: gloss, fullGloss: fullGloss, pos, classes, reconstructed, source: source, notes
        })
}

export function lookupWord(graph: string, id: number, data: LookupParameters) {
    return postToBackend(`${graph}/word/${id}/lookup`, data)
}

export function suggestParseCandidates(graph: string, id: number) {
    return postToBackend(`${graph}/word/${id}/parse`, {})
}

export function suggestCompound(graph: string, id: number, compoundId?: number) {
    return postToBackend(`${graph}/word/${id}/suggestCompound`, {compoundId})
}

export function deleteWord(graph: string, id: number) {
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

export function previewRuleChanges(graph, ruleId, newText) {
    return postToBackend(`${graph}/rule/${ruleId}/preview`, {newText})
}

export function deriveThroughRuleSequence(graph: string, wordId: number, seqId: number) {
    return postToBackend(`${graph}/word/${wordId}/derive`, {sequenceId: seqId})
}

export function addCorpusText(graph: string, lang: string, data: CorpusTextParams) {
    return postToBackend(`${graph}/corpus/${lang}/new`, data)
}

export function updateCorpusText(graph: string, id: number, data: CorpusTextParams) {
    return postToBackend(`${graph}/corpus/text/${id}`, data)
}

export function addTranslation(graph, corpusTextId, data) {
    return postToBackend(`${graph}/translation`, {corpusTextId: corpusTextId, ...data})
}

export function editTranslation(graph: string, id: number, data: any) {
    return postToBackend(`${graph}/translations/${id}`, data)
}

export function associateWord(graph, corpusTextId, wordId, index, contextGloss) {
    return postToBackend(`${graph}/corpus/text/${corpusTextId}/associate`, {wordId, index, contextGloss})
}

export function lockWordAssociations(graph: string, corpusTextId: number) {
    return postToBackend(`${graph}/corpus/text/${corpusTextId}/lockAssociations`, {})
}

export function acceptAlternative(graph, corpusTextId, index, wordId, ruleId) {
    return postToBackend(`${graph}/corpus/text/${corpusTextId}/accept`, {wordId: wordId, ruleId: ruleId, index: index})
}

export function addLink(graph: string, fromEntity: number, toEntity: number, linkType: string, ruleNames: string, source = null, notes = null) {
    return postToBackend(`${graph}/link`, {fromEntity, toEntity, linkType, ruleNames, source, notes})
}

export function addRuleLink(graph, fromEntity, toRuleName, linkType, source, notes) {
    return postToBackend(`${graph}/link/rule`,
        {fromEntity, toRuleName, linkType, source, notes}
    )
}

export function deleteLink(graph: string, fromEntity: number, toEntity: number, linkType: string) {
    return postToBackend(`${graph}/link/delete`, {fromEntity: fromEntity, toEntity: toEntity, linkType: linkType})
}

export function updateLink(graph, fromWord, toWord, linkType, ruleNames, source, notes) {
    return postToBackend(`${graph}/link/update`, {fromEntity: fromWord, toEntity: toWord, linkType: linkType, ruleNames: ruleNames, source: source, notes: notes})
}

export function createCompound(graph, compoundWord, firstComponentWord, source: string = null, notes: string = null) {
    return postToBackend(`${graph}/compound`, {compoundId: compoundWord, firstComponentId: firstComponentWord, source, notes})
}

export function addToCompound(graph, compoundId, componentWord, markHead) {
    return postToBackend(`${graph}/compound/${compoundId}/add`,{componentId: componentWord, markHead})
}

export function updateCompound(graph, compoundId, source, notes, head) {
    return postToBackend(`${graph}/compound/${compoundId}`, {source, notes, head})
}

export function deleteCompound(graph, compoundId) {
    return postToBackend(`${graph}/compound/${compoundId}/delete`, {})
}

export function addParadigm(graph, language, data) {
    return postToBackend(`${graph}/paradigms/${language}`, data)
}

export function updateParadigm(graph, id, data) {
    return postToBackend(`${graph}/paradigm/${id}`, data)
}

export function deleteParadigm(graph, id) {
    return postToBackend(`${graph}/paradigm/${id}/delete`, {})
}

export function updateWordParadigm(graph, id, paradigm) {
    return postToBackend(`${graph}/word/${id}/paradigm`, {items: [...paradigm]})
}

export function generateParadigm(graph, lang, data) {
    return postToBackend(`${graph}/paradigm/generate`, {lang: lang, ...data})
}

export function addLanguage(graph: string, name, shortName, reconstructed) {
    return postToBackend(`${graph}/languages`, {name: name, shortName: shortName, reconstructed: reconstructed})
}

export function updateLanguage(graph: string, lang: string, data) {
    return postToBackend(`${graph}/language/${lang}`, data)
}

export function addPhoneme(graph: string, lang: string, data) {
    return postToBackend(`${graph}/phonemes/${lang}`, data)
}

export function updatePhoneme(graph: string, id: number, data) {
    return postToBackend(`${graph}/phoneme/${id}`, data)
}

export function deletePhoneme(graph: string, id: number) {
    return postToBackend(`${graph}/phoneme/${id}/delete`, {})
}

export function copyPhonemes(graph: string, toLang: string, fromLang: string) {
    return postToBackend(`${graph}/language/${toLang}/copyPhonemes`, {fromLang: fromLang})
}

export function addPublication(graph: string, data: AddPublicationParameters): Promise<Response> {
    return postToBackend(`${graph}/publications`, data)
}

export function updatePublication(graph: string, id: number, data: AddPublicationParameters): Promise<Response> {
    return postToBackend(`${graph}/publication/${id}`, data)
}

export async function callApiAndRefresh(
    apiCall: () => Promise<Response>,
    router,
    setErrorText: (message: string) => void
){
    const result = await apiCall()
    if (result.status === 200) {
        router.replace(router.asPath)
    }
    else {
        const jr = await result.json()
        setErrorText(jr.message)
    }
}
