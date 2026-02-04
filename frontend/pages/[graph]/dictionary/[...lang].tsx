import {fetchBackend, allowEdit, fetchAllLanguagePaths} from "@/api";
import {useRouter} from "next/router";
import WordForm from "@/forms/WordForm";
import Breadcrumbs from "@/components/Breadcrumbs";
import {useState} from "react";
import {DictionaryViewModel, DictionaryWordViewModel, WordViewModel} from "@/models";
import WordLink from "@/components/WordLink";

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
    const graph = router.query.graph
    const filter = router.query.lang.length < 2 ? "" : router.query.lang[1]
    const [showAddWord, setShowAddWord] = useState(false)

    const filterText = filter === "names" ? "Names" :
        (filter === "reconstructed" ? "Reconstructed" :
        (filter === "compounds" ? "Compounds" : "Dictionary"))

    function submitted(r: WordViewModel) {
        if (r.gloss === "" || r.gloss === null) {
            router.push(`/${graph}/word/${r.language}/${r.text}`)
        }
        else {
            setShowAddWord(false)
            router.replace(router.asPath)
        }
    }


    function renderWordItem(w: DictionaryWordViewModel) {
        const gloss = w.fullGloss !== null && w.fullGloss !== "" ? w.fullGloss : w.ref.gloss;
        return <li key={w.ref.id}>
            <WordLink word={w.ref}/>
            {w.pos ? <> <i>{w.pos.toLowerCase()}.</i></> : ""} - {gloss}
        </li>
    }

    const grouped = dict.wordsByLetter;
    const letterKeys = Object.keys(grouped).sort();

    return <>
        <Breadcrumbs langId={dict.language} langName={dict.languageFullName} title={filterText}/>

        {allowEdit() && <>
            {!showAddWord && <button className="uiButton" onClick={() => setShowAddWord(!showAddWord)}>Add word</button>}
            {showAddWord && <WordForm languageReadOnly={true}
                      wordSubmitted={submitted}
                      cancelled={() => setShowAddWord(false)}
                      defaultValues={{
                          language: dict.language,
                          reconstructed: filter === "reconstructed",
                      }}
                      hideReconstructed={filter !== "reconstructed"}
            />}
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
                    {grouped[k].map(w => renderWordItem(w))}
                </ul>
            </section>
        ))}
    </>
}
