import WordForm from "@/components/WordForm";
import {useEffect} from "react";
import {fetchAllLanguagePaths, fetchBackend} from "@/api";
import {useRouter} from "next/router";
import Link from "next/link";

export async function getStaticProps(context) {
    return fetchBackend(`dictionary/${context.params.lang}`)
}

export async function getStaticPaths() {
    return await fetchAllLanguagePaths()
}

/*
export async function compoundLoader({params}) {
    return fetch(`${process.env.REACT_APP_BACKEND_URL}dictionary/${params.lang}/compounds`, { headers: { 'Accept': 'application/json'} })
}

export async function namesLoader({params}) {
    return fetch(`${process.env.REACT_APP_BACKEND_URL}dictionary/${params.lang}/names`, { headers: { 'Accept': 'application/json'} })
}

 */

export default function Dictionary(params) {
    const dict = params.loaderData
    const router = useRouter()

    const filterText = params.filter === "names" ? "Names" :
        (params.filter === "compounds" ? "Compounds" : "Dictionary")

    useEffect(() => { document.title = "Etymograph : " + dict.language.name + " : " + filterText})

    function submitted(word) {
        router.replace(router.asPath)
        if (word.gloss === "" || word.gloss === null) {
            router.push("/word/" + word.language + "/" + word.text)
        }
    }

    return <>
        <h2><small>
            <Link href={`/`}>Etymograph</Link> {'> '}
            <Link href={`/language/${dict.language.shortName}`}>{dict.language.name}</Link></small> {'>'} {filterText}</h2>
        <h3>Add word</h3>
        <WordForm language={dict.language.shortName} submitted={submitted}/>
        <ul>
            {dict.words.map(w => {
                let gloss = w.fullGloss !== null && w.fullGloss !== "" ? w.fullGloss : w.gloss;

                return <li key={w.id}>
                    <Link
                        href={`/word/${dict.language.shortName}/${w.text}${w.homonym ? "/" + w.id : ""}`}>{w.text}</Link> - {gloss}
                </li>;
            })}
        </ul>
    </>
}
