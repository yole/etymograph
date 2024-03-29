import WordForm from "@/components/WordForm";
import {useEffect, useState} from "react";
import {fetchBackend, allowEdit} from "@/api";
import {useRouter} from "next/router";
import Link from "next/link";

export const config = {
    unstable_runtimeJS: true
}

export async function getStaticProps(context) {
    const lang = context.params.lang
    if (lang.length === 2) {
        return fetchBackend(`dictionary/${lang[0]}/${lang[1]}`)
    }
    return fetchBackend(`dictionary/${lang[0]}`)
}

export async function getStaticPaths() {
    const {props} = await fetchBackend(`language`)
    const paths = props.loaderData.flatMap(lang => [
        {params: {lang: [lang.shortName]}},
        {params: {lang: [lang.shortName, 'compounds']}},
        {params: {lang: [lang.shortName, 'names']}},
        {params: {lang: [lang.shortName, 'reconstructed']}}
    ])
    return {paths, fallback: false}
}

export default function Dictionary(params) {
    const dict = params.loaderData
    const router = useRouter()
    const filter = router.query.lang.length < 2 ? "" : router.query.lang[1]

    const filterText = filter === "names" ? "Names" :
        (filter === "reconstructed" ? "Reconstructed" :
        (filter === "compounds" ? "Compounds" : "Dictionary"))

    const [errorText, setErrorText] = useState("")
    useEffect(() => { document.title = "Etymograph : " + dict.language.name + " : " + filterText})

    function submitted(status, r) {
        if (status !== 200) {
            setErrorText(r.message)
        }
        else {
            setErrorText("")
            router.replace(router.asPath)
            if (r.gloss === "" || r.gloss === null) {
                router.push("/word/" + r.language + "/" + r.text)
            }
        }
    }

    return <>
        <h2><small>
            <Link href={`/`}>Etymograph</Link> {'> '}
            <Link href={`/language/${dict.language.shortName}`}>{dict.language.name}</Link></small> {'>'} {filterText}</h2>
        {allowEdit() && <>
            <h3>Add word</h3>
            <WordForm language={dict.language.shortName}
                      languageReadOnly={true}
                      submitted={submitted}
                      initialReconstructed={filter === "reconstructed"}
                      hideReconstructed={filter === "reconstructed"}
            />
            <p/>
            {errorText !== "" && <div className="errorText">{errorText}</div>}
        </>}
        <ul>
            {dict.words.map(w => {
                let gloss = w.fullGloss !== null && w.fullGloss !== "" ? w.fullGloss : w.gloss;

                return <li key={w.id}>
                    <Link
                        href={`/word/${dict.language.shortName}/${w.text.toLowerCase()}${w.homonym ? "/" + w.id : ""}`}>{w.text}</Link> - {gloss}
                </li>;
            })}
        </ul>
    </>
}
