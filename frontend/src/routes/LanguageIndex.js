import {useLoaderData, useParams, useRevalidator} from "react-router";
import {useEffect, useState} from "react";
import {Link} from "react-router-dom";
import {updateLanguage} from "../api";

export async function loader({params}) {
    return fetch( `${process.env.REACT_APP_BACKEND_URL}language/${params.langId}`, { headers: { 'Accept': 'application/json'} })
}

export default function LanguageIndex() {
    const lang = useLoaderData()
    const params = useParams()
    const [editMode, setEditMode] = useState(false)
    const [letterNorm, setLetterNorm] = useState(lang.letterNormalization)
    const [diphthongs, setDiphthongs] = useState(lang.diphthongs.join(", "))
    const revalidator = useRevalidator()
    useEffect(() => { document.title = "Etymograph : " + lang.name })

    function saveLanguage() {
        updateLanguage(params.langId, letterNorm, diphthongs)
            .then(() => revalidator.revalidate())
        setEditMode(false)
    }

    return <>
        <h2><small><Link to={`/`}>Etymograph</Link> > </small>{lang.name}</h2>
        <h3>Orthography</h3>
        {!editMode && lang.letterNormalization != null && <p>Letter normalization: {lang.letterNormalization}</p>}
        {editMode && <table><tbody>
        <tr>
            <td><label>Letter normalization:</label></td>
            <td><input type="text" value={letterNorm} onChange={(e) => setLetterNorm(e.target.value)}/></td>
        </tr>
        </tbody></table>}

        <h3>Phonetics</h3>
        <ul>
        {lang.phonemeClasses.map(pc => <li>{pc.name}: {pc.matchingPhonemes.join(", ")}</li>)}
        </ul>
        {!editMode && lang.diphthongs.length > 0 && <p>Diphthongs: {lang.diphthongs.join(", ")}</p>}
        {editMode && <table><tbody>
        <tr>
            <td><label>Diphthongs:</label></td>
            <td><input type="text" value={diphthongs} onChange={(e) => setDiphthongs(e.target.value)}/></td>
        </tr>
        </tbody></table>}

        {editMode && <>
            <button onClick={() => saveLanguage()}>Save</button>&nbsp;
        </>}
        <button onClick={() => setEditMode(!editMode)}>{editMode ? "Cancel" : "Edit"}</button>
        <p/>
        <Link to={`/dictionary/${params.langId}`}>Dictionary</Link><br/>
        <Link to={`/dictionary/${params.langId}/compounds`}>Compound Words</Link><br/>
        <Link to={`/dictionary/${params.langId}/names`}>Names</Link><br/>
        <Link to={`/rules/${params.langId}`}>Rules</Link><br/>
        <Link to={`/paradigms/${params.langId}`}>Paradigms</Link><br/>
        <Link to={`/corpus/${params.langId}`}>Corpus</Link>
    </>
}
