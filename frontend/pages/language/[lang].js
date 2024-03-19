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
    const [grammaticalCategories, setGrammaticalCategories] = useState(lang.grammaticalCategories)
    const [wordClasses, setWordClasses] = useState(lang.wordClasses)
    const [diphthongs, setDiphthongs] = useState(lang.diphthongs.join(", "))
    const [syllableStructures, setSyllableStructures] = useState(lang.syllableStructures.join(", "))
    const [stressRule, setStressRule] = useState(lang.stressRuleName)
    const [phonotacticsRule, setPhonotacticsRule] = useState(lang.phonotacticsRuleName)
    const [orthographyRule, setOrthographyRule] = useState(lang.orthographyRuleName)
    const [errorText, setErrorText] = useState("")
    const router = useRouter()
    useEffect(() => { document.title = "Etymograph : " + lang.name })

    function saveLanguage() {
        updateLanguage(langId, diphthongs, syllableStructures, stressRule, phonotacticsRule, orthographyRule, grammaticalCategories, wordClasses)
            .then((r) => {
                if (r.status === 200) {
                    setErrorText("")
                    setEditMode(false)
                    router.replace(router.asPath)
                }
                else {
                    r.json().then(r => setErrorText(r.message))
                }
            })
    }

    return <>
        <h2><small><Link href={`/`}>Etymograph</Link> {'>'} </small>{lang.name}</h2>

        <Link href={`/dictionary/${langId}`}>Dictionary</Link>
        {' '}| <Link href={`/dictionary/${langId}/compounds`}>Compounds</Link>
        {' '}| <Link href={`/dictionary/${langId}/names`}>Names</Link>
        {' '}| <Link href={`/rules/${langId}`}>Rules</Link>
        {' '}| <Link href={`/paradigms/${langId}`}>Paradigms</Link>
        {' '}| <Link href={`/corpus/${langId}`}>Corpus</Link>

        <h3>Phonetics</h3>
        {lang.phonemes.map(pt => <>
            <h4>{pt.title}</h4>
            {(pt.rows.length > 1 || pt.columnTitles.length > 0) && <table className="phonemeTable">
                <thead><tr>
                    <th/>
                    {pt.columnTitles.map(ct => <th>{ct}</th>)}
                </tr></thead>
                <tbody>
                {pt.rows.map(pr => <tr>
                    <th scope="row">{pr.title}</th>
                    {pr.cells.map(pc => <td>
                        {pc.phonemes.map((p, i) => <>
                            {i > 0 && ", "}
                            <Link href={`/phoneme/${p.id}`}>{p.graphemes[0]}</Link>
                            {p.sound.length > 0 && ` /${p.sound}/`}
                        </>)}
                    </td>)}
                </tr>)}
                </tbody>
            </table>}
            {(pt.rows.length === 1 && pt.columnTitles.length === 0 && <ul>
                {pt.rows[0].cells.map(c => <li>
                    <Link href={`/phoneme/${c.phonemes[0].id}`}>{c.phonemes[0].graphemes.join(", ")}</Link>
                    {c.phonemes[0].sound.length > 0 && ` /${c.phonemes[0].sound}/`}
                    {' '}&ndash;{' '}
                    {c.phonemes[0].classes}
                </li>)}
            </ul>)}
        </>)}
        {allowEdit() && <Link href={`/phonemes/${langId}/new`}>Add phoneme</Link>}

        {!editMode && <>
            {lang.diphthongs.length > 0 && <p>Diphthongs: {lang.diphthongs.join(", ")}</p>}
            {lang.syllableStructures.length > 0 && <p>Syllable structures: {lang.syllableStructures.join(", ")}</p>}
            {lang.stressRuleName != null && <p>Stress rule: <Link href={`/rule/${lang.stressRuleId}`}>{lang.stressRuleName}</Link></p>}
            {lang.phonotacticsRuleName != null && <p>Phonotactics rule: <Link href={`/rule/${lang.phonotacticsRuleId}`}>{lang.phonotacticsRuleName}</Link></p>}
            {lang.orthographyRuleName != null && <p>Orthography rule: <Link href={`/rule/${lang.orthographyRuleId}`}>{lang.orthographyRuleName}</Link></p>}
        </>}
        {editMode && <>
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
                <td><label>Stress rule:</label></td>
                <td><input type="text" value={stressRule} onChange={(e) => setStressRule(e.target.value)}/></td>
            </tr>
            <tr>
                <td><label>Phonotactics rule:</label></td>
                <td><input type="text" value={phonotacticsRule} onChange={(e) => setPhonotacticsRule(e.target.value)}/></td>
            </tr>
            <tr>
                <td><label>Orthography rule:</label></td>
                <td><input type="text" value={orthographyRule} onChange={(e) => setOrthographyRule(e.target.value)}/></td>
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

        {(editMode || wordClasses.trim().length > 0) && <h4>Word classes</h4>}
        {!editMode && wordClasses.trim().length > 0 && <>
            <ul>
                {wordClasses.split('\n').map(s => <li>{s}</li>)}
            </ul>
        </>}
        {editMode && <>
            <textarea rows={5} cols={50} value={wordClasses} onChange={(e) => setWordClasses(e.target.value)}/>
            <br/>
        </>}

        {editMode && <>
            <button onClick={() => saveLanguage()}>Save</button>&nbsp;
        </>}
        {allowEdit() &&
           <button onClick={() => setEditMode(!editMode)}>{editMode ? "Cancel" : "Edit"}</button>
        }
        <p/>
        {errorText !== "" && <div className="errorText">{errorText}</div>}
    </>
}
