import {useLoaderData, useNavigate, useRevalidator} from "react-router";
import {Link} from "react-router-dom";
import WordForm from "./WordForm";
import {useEffect} from "react";

export async function loader({params}) {
    return fetch(`${process.env.REACT_APP_BACKEND_URL}dictionary/${params.lang}`, { headers: { 'Accept': 'application/json'} })
}

export async function compoundLoader({params}) {
    return fetch(`${process.env.REACT_APP_BACKEND_URL}dictionary/${params.lang}/compounds`, { headers: { 'Accept': 'application/json'} })
}

export async function namesLoader({params}) {
    return fetch(`${process.env.REACT_APP_BACKEND_URL}dictionary/${params.lang}/names`, { headers: { 'Accept': 'application/json'} })
}

export default function Dictionary(params) {
    const dict = useLoaderData()
    const revalidator = useRevalidator()
    const navigate = useNavigate()

    const filterText = params.filter === "names" ? "Names" :
        (params.filter === "compounds" ? "Compounds" : "Dictionary")

    useEffect(() => { document.title = "Etymograph : " + dict.language.name + " : " + filterText})

    function submitted(word) {
        revalidator.revalidate()
        if (word.gloss === "" || word.gloss === null) {
            navigate("/word/" + word.language + "/" + word.text)
        }
    }

    return <>
        <h2><small><Link to={`/language/${dict.language.shortName}`}>{dict.language.name}</Link></small> > {filterText}</h2>
        <h3>Add word</h3>
        <WordForm language={dict.language.shortName} submitted={submitted}/>
        <ul>
            {dict.words.map(w => {
                let gloss = w.fullGloss !== null && w.fullGloss !== "" ? w.fullGloss : w.gloss;

                return <li key={w.id}>
                    <Link
                        to={`/word/${dict.language.shortName}/${w.text}${w.homonym ? "/" + w.id : ""}`}>{w.text}</Link> - {gloss}
                </li>;
            })}
        </ul>
    </>
}
