import {
    AddPublicationParameters, ComparePhonemesResult,
    CorpusTextParams,
    LookupParameters,
    LookupResultViewModel,
    SuggestCompoundViewModel,
    UpdateLanguageParameters,
    UpdateParadigmParameters, UpdatePhonemeParameters,
    UpdateRuleParameters,
    UpdateSequenceParams
} from "@/models";

export function allowEdit() {
    return process.env.NEXT_PUBLIC_READONLY !== "true";
}

export async function fetchBackend(graph: string, url: string, withGlobalState = false) {
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
        const allRulesJson = await allRules.json()
        const allInputAssists = await fetch(`${process.env.NEXT_PUBLIC_BACKEND_URL}${graph}/inputAssist`, { headers: { 'Accept': 'application/json'} })
        const allInputAssistsJson = await allInputAssists.json()
        return {
            props: {
                loaderData,
                globalState: {
                    graphs: allGraphsJson,
                    languages: allLanguagesJson,
                    rules: allRulesJson,
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
    if (response.status !== 200) {
        const jr = await response.json()
        throw new Error(jr.message)
    }
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

export async function fetchAlternatives(graph: string, corpusTextId: number, index: number) {
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

class TypedResponse<Result> {
    private response: Response

    constructor(response: Response) {
        this.response = response
    }

    ok(): boolean {
        return this.response.status === 200
    }

    async result(): Promise<Result> {
        return await this.response.json() as Result
    }

    async error(): Promise<string> {
        const jr = await this.response.json()
        return jr.message
    }
}

async function postToBackendTyped<Result>(endpoint: string, data: any): Promise<TypedResponse<Result>> {
    const result = await postToBackend(endpoint, data)
    return new TypedResponse(result)
}

export function addWord(
    graph: string,
    lang: string,
    text: string,
    gloss?: string,
    fullGloss?: string,
    pos?: string,
    classes?: string,
    reconstructed: boolean = false,
    source: string = null,
    notes: string = null,
    forceNew: boolean = false
) {
    return postToBackend(`${graph}/word/${lang}`,
        {text: text, gloss: gloss, fullGloss: fullGloss, pos, classes, reconstructed, source, notes, forceNew}
    )
}

export function updateWord(
    graph: string,
    id: number,
    text: string,
    gloss: string,
    fullGloss: string,
    pos: string,
    classes: string,
    reconstructed: boolean,
    source: string,
    notes: string
) {
    return postToBackend(`${graph}/word/${id}/update`,
        {
            text: text, gloss: gloss, fullGloss: fullGloss, pos, classes, reconstructed, source: source, notes
        })
}

export function lookupWord(graph: string, id: number, data: LookupParameters): Promise<TypedResponse<LookupResultViewModel>> {
    return postToBackendTyped(`${graph}/word/${id}/lookup`, data)
}

export function suggestParseCandidates(graph: string, id: number) {
    return postToBackend(`${graph}/word/${id}/parse`, {})
}

export function suggestCompound(graph: string, id: number, compoundId?: number): Promise<TypedResponse<SuggestCompoundViewModel>> {
    return postToBackendTyped(`${graph}/word/${id}/suggestCompound`, {compoundId})
}

export function deleteWord(graph: string, id: number) {
    return postToBackend(`${graph}/word/${id}/delete`, {})
}

export function addWordSequence(graph: string, text: string, source: string) {
    return postToBackend(`${graph}/wordSequence`, {sequence: text, source})
}

export function addRule(graph: string, data: UpdateRuleParameters) {
    return postToBackend(`${graph}/rule`, data)
}

export function updateRule(graph: string, id: number, data: UpdateRuleParameters) {
    return postToBackend(`${graph}/rule/${id}`, data)
}

export function deleteRule(graph: string, id: number) {
    return postToBackend(`${graph}/rule/${id}/delete`, {})
}

export function addRuleSequence(graph: string, data: UpdateSequenceParams) {
    return postToBackend(`${graph}/rule/sequence`, data)
}

export function updateRuleSequence(graph: string, id: number, data: UpdateSequenceParams): Promise<Response> {
    return postToBackend(`${graph}/rule/sequence/${id}`, data)
}

export function applyRuleSequence(graph: string, seqId: number, fromWordId: number, toWordId: number) {
    return postToBackend(`${graph}/rule/sequence/${seqId}/apply`, {
        linkFromId: fromWordId,
        linkToId: toWordId
    })
}

export function reapplyRuleSequence(graph: string, seqId: number) {
    return postToBackend(`${graph}/rule/sequence/${seqId}/reapply`, {})
}

export function traceRule(graph: string, ruleId: number, word: string, reverse: boolean, language?: string): Promise<Response> {
    return postToBackend(`${graph}/rule/${ruleId}/trace`, {word, reverse, language})
}

export function previewRuleChanges(graph: string, ruleId: number, newText: string) {
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

export function addTranslation(graph: string, corpusTextId: number, data) {
    return postToBackend(`${graph}/translation`, {corpusTextId: corpusTextId, ...data})
}

export function editTranslation(graph: string, id: number, data: any) {
    return postToBackend(`${graph}/translations/${id}`, data)
}

export function associateWord(graph: string, corpusTextId: number, wordId: number, index: number, contextGloss?: string) {
    return postToBackend(`${graph}/corpus/text/${corpusTextId}/associate`, {wordId, index, contextGloss})
}

export function lockWordAssociations(graph: string, corpusTextId: number) {
    return postToBackend(`${graph}/corpus/text/${corpusTextId}/lockAssociations`, {})
}

export function acceptAlternative(graph: string, corpusTextId: number, index: number, wordId: number, ruleId: number) {
    return postToBackend(`${graph}/corpus/text/${corpusTextId}/accept`, {wordId: wordId, ruleId: ruleId, index: index})
}

export function addLink(graph: string, fromEntity: number, toEntity: number, linkType: string, ruleNames: string, source = null, notes = null) {
    return postToBackend(`${graph}/link`, {fromEntity, toEntity, linkType, ruleNames, source, notes})
}

export function addRuleLink(graph: string, fromEntity: number, toRuleName: string, linkType: string, source?: string, notes?: string) {
    return postToBackend(`${graph}/link/rule`,
        {fromEntity, toRuleName, linkType, source, notes}
    )
}

export function deleteLink(graph: string, fromEntity: number, toEntity: number, linkType: string) {
    return postToBackend(`${graph}/link/delete`, {fromEntity: fromEntity, toEntity: toEntity, linkType: linkType})
}

export function refreshLinkSequence(graph: string, fromEntity: number, toEntity: number, linkType: string) {
    return postToBackend(`${graph}/link/refreshSequence`, {fromEntity: fromEntity, toEntity: toEntity, linkType: linkType})
}

export function updateLink(graph: string, fromWord: number, toWord: number, linkType: string, ruleNames: string, source, notes) {
    return postToBackend(`${graph}/link/update`, {fromEntity: fromWord, toEntity: toWord, linkType: linkType, ruleNames: ruleNames, source: source, notes: notes})
}

export function createCompound(graph: string, compoundWord: number, firstComponentWord: number, source: string = null, notes: string = null) {
    return postToBackend(`${graph}/compound`, {compoundId: compoundWord, firstComponentId: firstComponentWord, source, notes})
}

export function addToCompound(graph: string, compoundId: number, componentWord: number, markHead: boolean) {
    return postToBackend(`${graph}/compound/${compoundId}/add`,{componentId: componentWord, markHead})
}

export function updateCompound(graph: string, compoundId: number, source?: string, notes?: string, head?: boolean) {
    return postToBackend(`${graph}/compound/${compoundId}`, {source, notes, head})
}

export function deleteCompound(graph: string, compoundId: number) {
    return postToBackend(`${graph}/compound/${compoundId}/delete`, {})
}

export function addParadigm(graph: string, language: string, data: UpdateParadigmParameters) {
    return postToBackend(`${graph}/paradigms/${language}`, data)
}

export function updateParadigm(graph: string, id: number, data: UpdateParadigmParameters) {
    return postToBackend(`${graph}/paradigm/${id}`, data)
}

export function deleteParadigm(graph: string, id: UpdateParadigmParameters) {
    return postToBackend(`${graph}/paradigm/${id}/delete`, {})
}

export function updateWordParadigm(graph: string, id: number, paradigm: any) {
    return postToBackend(`${graph}/word/${id}/paradigm`, {items: [...paradigm]})
}

export function generateParadigm(graph: string, lang: string, data) {
    return postToBackend(`${graph}/paradigm/generate`, {lang: lang, ...data})
}

export function addLanguage(graph: string, name: string, shortName: string, reconstructed: boolean) {
    return postToBackend(`${graph}/languages`, {name: name, shortName: shortName, reconstructed: reconstructed})
}

export function updateLanguage(graph: string, lang: string, data: UpdateLanguageParameters) {
    return postToBackend(`${graph}/language/${lang}`, data)
}

export function addPhoneme(graph: string, lang: string, data: UpdatePhonemeParameters) {
    return postToBackend(`${graph}/phonemes/${lang}`, data)
}

export function updatePhoneme(graph: string, id: number, data: UpdatePhonemeParameters) {
    return postToBackend(`${graph}/phoneme/${id}`, data)
}

export function deletePhoneme(graph: string, id: number) {
    return postToBackend(`${graph}/phoneme/${id}/delete`, {})
}

export function comparePhonemes(graph: string, id: number, toPhoneme: string) {
    return postToBackendTyped<ComparePhonemesResult>(`${graph}/phoneme/${id}/compare`, {toPhoneme})

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
