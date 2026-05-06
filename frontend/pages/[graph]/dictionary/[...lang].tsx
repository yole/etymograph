import {fetchBackend, fetchAllLanguagePaths, allowEditGraph} from "@/api";
import {useRouter} from "next/router";
import WordForm from "@/forms/WordForm";
import Breadcrumbs from "@/components/Breadcrumbs";
import {useContext, useState} from "react";
import {DictionaryViewModel, DictionaryWordViewModel, WordViewModel} from "@/models";
import WordLink from "@/components/WordLink";
import LanguageNavBar from "@/components/LanguageNavBar";
import WordSequenceForm from "@/forms/WordSequenceForm";
import {GlobalStateContext} from "@/components/Contexts";
import {Select} from "@mantine/core";

export const config = {
    unstable_runtimeJS: true
}

export async function getStaticProps(context) {
    const graph = context.params.graph
    const lang = context.params.lang
    if (lang.length === 2) {
        return fetchBackend(graph, `dictionary/${lang[0]}/${lang[1]}`, true)
    }
    return fetchBackend(graph, `dictionary/${lang[0]}`, true)
}

export async function getStaticPaths() {
    const langPaths = await fetchAllLanguagePaths()
    const paths = langPaths.paths.flatMap(p => [
        {params: {graph: p.params.graph, lang: [p.params.lang]}},
        {params: {graph: p.params.graph, lang: [p.params.lang, 'compounds']}},
        {params: {graph: p.params.graph, lang: [p.params.lang, 'names']}},
        {params: {graph: p.params.graph, lang: [p.params.lang, 'reconstructed']}}
    ])
    return {paths, fallback: false}
}

export default function Dictionary(params) {
    const dict = params.loaderData as DictionaryViewModel
    const router = useRouter()
    const graph = router.query.graph as string
    const langPath = Array.isArray(router.query.lang) ? router.query.lang : [dict.language]
    const filter = langPath.length < 2 ? "" : langPath[1]
    const selectedPos = typeof router.query.pos === "string" ? router.query.pos : ""
    const globalState = useContext(GlobalStateContext)
    const canEdit = allowEditGraph()
    const [showAddWord, setShowAddWord] = useState(false)
    const [showAddSequence, setShowAddSequence] = useState(false)
    const language = globalState?.languages.find(l => l.shortName === dict.language)
    const definedPosOptions = language?.pos.map((p) => ({
        value: p.abbreviation,
        label: `${p.name} (${p.abbreviation})`
    })) ?? []
    const definedPosValues = new Set(definedPosOptions.map((p) => p.value))
    const wordPosOptions = Array.from(new Set(dict.words
        .map((w) => w.pos)
        .filter((pos): pos is string => pos !== undefined && pos !== null && pos !== "")))
        .filter((pos) => !definedPosValues.has(pos))
        .sort()
        .map((pos) => ({value: pos, label: pos}))
    const knownPosValues = new Set(definedPosOptions.concat(wordPosOptions).map((p) => p.value))
    const selectedUrlPosOption = selectedPos && !knownPosValues.has(selectedPos)
        ? [{value: selectedPos, label: selectedPos}]
        : []
    const posOptions = [{value: "", label: "All parts of speech"}].concat(definedPosOptions, wordPosOptions, selectedUrlPosOption)

    const filterText = filter === "names" ? "Names" :
        (filter === "reconstructed" ? "Reconstructed" :
        (filter === "compounds" ? "Compounds" : "Dictionary"))

    function updatePosFilter(pos: string) {
        router.push({
            pathname: router.pathname,
            query: {...router.query, pos: pos || undefined}
        }, undefined, {shallow: true})
    }

    function submitted(r: WordViewModel) {
        if (r.gloss === "" || r.gloss === null) {
            router.push(`/${graph}/word/${r.language}/${r.text}`)
        }
        else {
            setShowAddWord(false)
            router.replace(router.asPath)
        }
    }

    function exampleSubmitted(r, data) {
        setShowAddSequence(false)
        router.replace(router.asPath)
    }

    function renderWordItem(w: DictionaryWordViewModel) {
        const gloss = w.fullGloss !== null && w.fullGloss !== "" ? w.fullGloss : w.ref.gloss;
        return <li key={w.ref.id}>
            <WordLink word={w.ref}/>
            {w.pos ? <> <i>{w.pos.toLowerCase()}.</i></> : ""} - {gloss}
        </li>
    }

    const grouped = dict.wordsByLetter;
    const filteredGrouped = Object.fromEntries(Object.entries(grouped)
        .map(([letter, words]) => [letter, selectedPos ? words.filter((w) => w.pos === selectedPos) : words])
        .filter(([, words]) => words.length > 0))
    const letterKeys = Object.keys(filteredGrouped).sort((a, b) => {
        const aIsUpper = a === a.toUpperCase() && a !== a.toLowerCase();
        const bIsUpper = b === b.toUpperCase() && b !== b.toLowerCase();
        if (aIsUpper !== bIsUpper) {
            return aIsUpper ? 1 : -1;
        }
        return a.localeCompare(b);
    });

    return <>
        <Breadcrumbs langId={dict.language} langName={dict.languageFullName} title={filterText}/>
        <LanguageNavBar langId={dict.language}/>
        <p/>

        <div style={{maxWidth: '24rem', margin: '0.5rem 0 1rem'}}>
            <Select id="dictionary-pos-filter"
                    label="Part of speech"
                    data={posOptions}
                    value={selectedPos}
                    onChange={(val) => updatePosFilter(val ?? "")}
                    searchable={true}
                    allowDeselect={false}
            />
        </div>

        {canEdit && <>
            {!showAddWord && <><button className="uiButton" onClick={() => setShowAddWord(!showAddWord)}>Add word</button>{' '}</>}
            {!showAddSequence && <><button className="uiButton" onClick={() => {
                setShowAddSequence(!showAddSequence)
            }}>Add derivation</button>{' '}</>}
            {showAddWord && <WordForm languageReadOnly={true}
                      wordSubmitted={submitted}
                      cancelled={() => setShowAddWord(false)}
                      defaultValues={{
                          language: dict.language,
                          reconstructed: filter === "reconstructed",
                      }}
                      hideReconstructed={filter !== "reconstructed"}
                      showSyllabographic={dict.languageSyllabographic}
            />}
            {showAddSequence &&
                <WordSequenceForm
                    focusTarget={'exampleText'}
                    submitted={exampleSubmitted}
                    cancelled={() => setShowAddSequence(false)}
                />
            }
        </>}

        {/* Letter navigation bar */}
        <div style={{display: 'flex', flexWrap: 'wrap', gap: '0.5rem', margin: '0.5rem 0'}}>
            {letterKeys.map(k => (
                <a key={k} href={`#letter-${encodeURIComponent(k)}`}
                   style={{textDecoration: 'none', padding: '0.25rem 0.5rem', border: '1px solid #ccc', borderRadius: '4px'}}>
                    {k}
                </a>
            ))}
        </div>

        {/* Grouped sections */}
        {letterKeys.map(k => (
            <section key={k} id={`letter-${k}`}>
                <h3>{k}</h3>
                <ul>
                    {filteredGrouped[k].map(w => renderWordItem(w))}
                </ul>
            </section>
        ))}
    </>
}
