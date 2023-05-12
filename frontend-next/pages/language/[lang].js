import Link from "next/link";
import {useEffect, useState} from "react";
import {fetchBackend, updateLanguage, fetchAllLanguagePaths} from "@/api";
import {useRouter} from "next/router";

export const config = {
    unstable_runtimeJS: true
}

export async function getStaticProps(context) {
    return fetchBackend(`language/${context.params.lang}`)
}

export async function getStaticPaths() {
    return await fetchAllLanguagePaths();
}

export default function LanguageIndex(props) {
    const lang = props.loaderData
    const langId = lang.shortName
    const allowEdit = process.env.NEXT_PUBLIC_READONLY !== "true"
    const [editMode, setEditMode] = useState(false)
    const [letterNorm, setLetterNorm] = useState(lang.letterNormalization)
    const [digraphs, setDigraphs] = useState(lang.digraphs.join(", "))
    const [diphthongs, setDiphthongs] = useState(lang.diphthongs.join(", "))
    const [syllableStructures, setSyllableStructures] = useState(lang.syllableStructures.join(", "))
    const [wordFinals, setWordFinals] = useState(lang.wordFinals.join(", "))
    const [phonemeClasses, setPhonemeClasses] = useState(
        lang.phonemeClasses.map(pc => `${pc.name}: ${pc.matchingPhonemes.join(",")}`).join("\n")
    )
    const [stressRule, setStressRule] = useState(lang.stressRuleName)
    const router = useRouter()
    useEffect(() => { document.title = "Etymograph : " + lang.name })

    function saveLanguage() {
        updateLanguage(langId, letterNorm, digraphs, phonemeClasses, diphthongs, syllableStructures, wordFinals, stressRule)
            .then(() => router.replace(router.asPath))
        setEditMode(false)
    }

    return <>
        <h2><small><Link href={`/`}>Etymograph</Link> {'>'} </small>{lang.name}</h2>
        <h3>Orthography</h3>
        {!editMode && <>
            {lang.letterNormalization != null && <p>Letter normalization: {lang.letterNormalization}</p>}
            {lang.digraphs.length > 0 && <p>Digraphs: {lang.digraphs.join(", ")}</p>}
        </>}
        {editMode && <table><tbody>
        <tr>
            <td><label>Letter normalization:</label></td>
            <td><input type="text" value={letterNorm} onChange={(e) => setLetterNorm(e.target.value)}/></td>
        </tr>
        <tr>
            <td><label>Digraphs:</label></td>
            <td><input type="text" value={digraphs} onChange={(e) => setDigraphs(e.target.value)}/></td>
        </tr>
        </tbody></table>}

        <h3>Phonetics</h3>
        <h4>Phoneme classes</h4>
        {!editMode && <>
            <ul>
                {lang.phonemeClasses.map(pc => <li key={pc.name}>{pc.name}: {pc.matchingPhonemes.join(", ")}</li>)}
            </ul>
            {lang.diphthongs.length > 0 && <p>Diphthongs: {lang.diphthongs.join(", ")}</p>}
            {lang.syllableStructures.length > 0 && <p>Syllable structures: {lang.syllableStructures.join(", ")}</p>}
            {lang.wordFinals.length > 0 && <p>Word finals: {lang.wordFinals.join(", ")}</p>}
            {lang.stressRuleName != null && <p>Stress rule: {lang.stressRuleName}</p>}
        </>}
        {editMode && <>
            <textarea rows={5} cols={50} value={phonemeClasses} onChange={(e) => setPhonemeClasses(e.target.value)}/>
            <table><tbody>
            <tr>
                <td><label>Diphthongs:</label></td>
                <td><input type="text" value={diphthongs} onChange={(e) => setDiphthongs(e.target.value)}/></td>
            </tr>
            <tr>
                <td><label>Syllable structures:</label></td>
                <td><input type="text" value={syllableStructures} onChange={(e) => setSyllableStructures(e.target.value)}/></td>
            </tr>
            <tr>
                <td><label>Word finals:</label></td>
                <td><input type="text" value={wordFinals} onChange={(e) => setWordFinals(e.target.value)}/></td>
            </tr>
            <tr>
                <td><label>Stress rule:</label></td>
                <td><input type="text" value={stressRule} onChange={(e) => setStressRule(e.target.value)}/></td>
            </tr>
            </tbody></table>
        </>}

        {editMode && <>
            <button onClick={() => saveLanguage()}>Save</button>&nbsp;
        </>}
        {allowEdit &&
           <button onClick={() => setEditMode(!editMode)}>{editMode ? "Cancel" : "Edit"}</button>
        }
        <p/>
        <Link href={`/dictionary/${langId}`}>Dictionary</Link><br/>
        <Link href={`/dictionary/${langId}/compounds`}>Compound Words</Link><br/>
        <Link href={`/dictionary/${langId}/names`}>Names</Link><br/>
        <Link href={`/rules/${langId}`}>Rules</Link><br/>
        <Link href={`/paradigms/${langId}`}>Paradigms</Link><br/>
        <Link href={`/corpus/${langId}`}>Corpus</Link>
    </>
}