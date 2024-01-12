import Link from "next/link";
import {useEffect, useState} from "react";
import {fetchBackend, updateLanguage, fetchAllLanguagePaths, allowEdit} from "@/api";
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
    const [editMode, setEditMode] = useState(false)
    const [phonemes, setPhonemes] = useState(lang.phonemes)
    const [grammaticalCategories, setGrammaticalCategories] = useState(lang.grammaticalCategories)
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
        updateLanguage(langId, phonemes, phonemeClasses, diphthongs, syllableStructures, wordFinals, stressRule, grammaticalCategories)
            .then(() => router.replace(router.asPath))
        setEditMode(false)
    }

    return <>
        <h2><small><Link href={`/`}>Etymograph</Link> {'>'} </small>{lang.name}</h2>

        <h3>Phonetics</h3>
        {(editMode || phonemes.trim().length > 0) && <h4>Phonemes</h4>}
        {!editMode && phonemes.trim().length > 0 && <>
            <ul>
                {phonemes.split('\n').map(s => <li>{s}</li>)}
            </ul>
        </>}
        {editMode && <>
            <textarea rows={5} cols={50} value={phonemes} onChange={(e) => setPhonemes(e.target.value)}/>
            <br/>
        </>}

        <h4>Phoneme classes</h4>
        {!editMode && <>
            <ul>
                {lang.phonemeClasses.map(pc => <li key={pc.name}>{pc.name}: {pc.matchingPhonemes.join(", ")}</li>)}
            </ul>
            {lang.diphthongs.length > 0 && <p>Diphthongs: {lang.diphthongs.join(", ")}</p>}
            {lang.syllableStructures.length > 0 && <p>Syllable structures: {lang.syllableStructures.join(", ")}</p>}
            {lang.wordFinals.length > 0 && <p>Word finals: {lang.wordFinals.join(", ")}</p>}
            {lang.stressRuleName != null && <p>Stress rule: <Link href={`/rule/${lang.stressRuleId}`}>{lang.stressRuleName}</Link></p>}
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

        <h3>Grammar</h3>
        {(editMode || grammaticalCategories.trim().length > 0) && <h4>Grammatical categories</h4>}
        {!editMode && grammaticalCategories.trim().length > 0 && <>
            <ul>
                {grammaticalCategories.split('\n').map(s => <li>{s}</li>)}
            </ul>
        </>}
        {editMode && <>
            <textarea rows={5} cols={50} value={grammaticalCategories} onChange={(e) => setGrammaticalCategories(e.target.value)}/>
            <br/>
        </>}

        {editMode && <>
            <button onClick={() => saveLanguage()}>Save</button>&nbsp;
        </>}
        {allowEdit() &&
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
