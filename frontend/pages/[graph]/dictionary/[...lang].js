import {fetchBackend, allowEdit, fetchAllLanguagePaths} from "@/api";
import {useRouter} from "next/router";
import Link from "next/link";
import WordForm from "@/forms/WordForm";
import Breadcrumbs from "@/components/Breadcrumbs";
import {useState} from "react";

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
    const dict = params.loaderData
    const router = useRouter()
    const graph = router.query.graph
    const filter = router.query.lang.length < 2 ? "" : router.query.lang[1]
    const [showAddWord, setShowAddWord] = useState(false)

    const filterText = filter === "names" ? "Names" :
        (filter === "reconstructed" ? "Reconstructed" :
        (filter === "compounds" ? "Compounds" : "Dictionary"))

    function submitted(r) {
        if (r.gloss === "" || r.gloss === null) {
            router.push(`/${graph}/word/${r.language}/${r.text}`)
        }
        else {
            setShowAddWord(false)
            router.replace(router.asPath)
        }
    }

    return <>
        <Breadcrumbs langId={dict.language.shortName} langName={dict.language.name} title={filterText}/>

        {allowEdit() && <>
            {!showAddWord && <button className="inlineButton inlineButtonNormal" onClick={() => setShowAddWord(!showAddWord)}>Add word</button>}
            {showAddWord && <WordForm languageReadOnly={true}
                      submitted={submitted}
                      cancelled={() => setShowAddWord(false)}
                      defaultValues={{
                          language: dict.language.shortName,
                          reconstructed: filter === "reconstructed",
                      }}
                      hideReconstructed={filter !== "reconstructed"}
            />}
        </>}
        <ul>
            {dict.words.map(w => {
                let gloss = w.fullGloss !== null && w.fullGloss !== "" ? w.fullGloss : w.gloss;

                return <li key={w.id}>
                    <Link
                        href={`/${graph}/word/${dict.language.shortName}/${w.text.toLowerCase()}${w.homonym ? "/" + w.id : ""}`}>{w.text}</Link> - {gloss}
                </li>;
            })}
        </ul>
    </>
}
