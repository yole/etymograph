import Link from "next/link";
import {useState} from "react";
import {fetchBackend, updateLanguage, fetchAllLanguagePaths, allowEdit, copyPhonemes} from "@/api";
import {useRouter} from "next/router";
import Breadcrumbs from "@/components/Breadcrumbs";
import EtymographFormView, {View} from "@/components/EtymographFormView";
import EtymographForm from "@/components/EtymographForm";
import FormRow from "@/components/FormRow";
import FormTextArea from "@/components/FormTextArea";

export const config = {
    unstable_runtimeJS: true
}

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph, `language/${context.params.lang}`, true)
}

export const getStaticPaths = fetchAllLanguagePaths

export default function LanguageIndex(props) {
    const lang = props.loaderData
    const langId = lang.shortName
    const [errorText, setErrorText] = useState("")
    const [showCopyPhonemesForm, setShowCopyPhonemesForm] = useState(false)
    const [copyPhonemesFrom, setCopyPhonemesFrom] = useState("")
    const router = useRouter()
    const graph = router.query.graph

    async function copyPhonemesClicked() {
        const r = await copyPhonemes(graph, langId, copyPhonemesFrom)
        if (r.status === 200) {
            setErrorText("")
            router.replace(router.asPath)
        }
        else {
            r.json().then(r => setErrorText(r.message))
        }
    }

    return <>
        <Breadcrumbs title={lang.name + (lang.reconstructed ? " (reconstructed)" : "")}/>

        <Link href={`/${graph}/dictionary/${langId}`}>Dictionary</Link>
        {' '}| <Link href={`/${graph}/dictionary/${langId}/compounds`}>Compounds</Link>
        {' '}| <Link href={`/${graph}/dictionary/${langId}/names`}>Names</Link>
        {' '}| <Link href={`/${graph}/dictionary/${langId}/reconstructed`}>Reconstructed words</Link>
        {' '}| <Link href={`/${graph}/rules/${langId}`}>Rules</Link>
        {' '}| <Link href={`/${graph}/paradigms/${langId}`}>Paradigms</Link>
        {' '}| <Link href={`/${graph}/corpus/${langId}`}>Corpus</Link>

        <h3>Phonetics</h3>
        {lang.phonemes.map(pt => <>
            <h4>{pt.title}</h4>
            {(pt.rows.length > 1 || pt.columnTitles.length > 0) && <table className="tableWithBorders phonemeTable">
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
                            <Link href={`/${graph}/phoneme/${p.id}`}>{p.graphemes[0]}</Link>
                            {p.sound.length > 0 && ` /${p.sound}/`}
                        </>)}
                    </td>)}
                </tr>)}
                </tbody>
            </table>}
            {(pt.rows.length === 1 && pt.columnTitles.length === 0 && <ul>
                {pt.rows[0].cells.map(c => <li>
                    <Link href={`/${graph}/phoneme/${c.phonemes[0].id}`}>{c.phonemes[0].graphemes.join(", ")}</Link>
                    {c.phonemes[0].sound.length > 0 && ` /${c.phonemes[0].sound}/`}
                    {' '}&ndash;{' '}
                    {c.phonemes[0].classes}
                </li>)}
            </ul>)}
        </>)}
        {allowEdit() && <>
            <p/>
            <button onClick={() => router.push(`/${graph}/phonemes/${langId}/new`)}>Add phoneme</button>
            {lang.phonemes.length === 0 &&<>
                {' '}
                <button className="inlineButton" onClick={() => setShowCopyPhonemesForm(!showCopyPhonemesForm)}>Copy phonemes</button>
                {showCopyPhonemesForm && <>
                    <p/>
                    Copy phonemes from: <input type="text" value={copyPhonemesFrom} onChange={(e) => setCopyPhonemesFrom(e.target.value)}/><p/>
                    <button onClick={() => copyPhonemesClicked()}>Copy</button>
                </>}
            </>}
        </>}

        <EtymographFormView>
            <View>
                {lang.diphthongs.length > 0 && <p>Diphthongs: {lang.diphthongs.join(", ")}</p>}
                {lang.syllableStructures.length > 0 && <p>Syllable structures: {lang.syllableStructures.join(", ")}</p>}
                {lang.stressRuleName != null && <p>Stress rule: <Link href={`/${graph}/rule/${lang.stressRuleId}`}>{lang.stressRuleName}</Link></p>}
                {lang.phonotacticsRuleName != null && <p>Phonotactics rule: <Link href={`/${graph}/rule/${lang.phonotacticsRuleId}`}>{lang.phonotacticsRuleName}</Link></p>}
                {lang.pronunciationRuleName != null && <p>Pronunciation rule: <Link href={`/${graph}/rule/${lang.pronunciationRuleId}`}>{lang.pronunciationRuleName}</Link></p>}
                {lang.orthographyRuleName != null && <p>Orthography rule: <Link href={`/${graph}/rule/${lang.orthographyRuleId}`}>{lang.orthographyRuleName}</Link></p>}

                <h3>Grammar</h3>
                {lang.grammaticalCategories.trim().length > 0 && <>
                    <h4>Grammatical categories</h4>
                    <ul>
                        {lang.grammaticalCategories.split('\n').map(s => <li>{s}</li>)}
                    </ul>
                </>}
                {lang.wordClasses.trim().length > 0 && <>
                    <h4>Word classes</h4>
                    <ul>
                        {lang.wordClasses.split('\n').map(s => <li>{s}</li>)}
                    </ul>
                </>}
            </View>
            <EtymographForm
                    updateId={langId}
                    update={(data) => updateLanguage(graph, langId, data)}
                    defaultValues={{
                        diphthongs: lang.diphthongs.join(", "),
                        syllableStructures: lang.syllableStructures.join(", "),
                        stressRuleName: lang.stressRuleName,
                        phonotacticsRuleName: lang.phonotacticsRuleName,
                        pronunciationRuleName: lang.pronunciationRuleName,
                        orthographyRuleName: lang.orthographyRuleName,
                        grammaticalCategories: lang.grammaticalCategories,
                        wordClasses: lang.wordClasses
                    }}
            >
                <table><tbody>
                    <FormRow label="Diphthongs" id="diphthongs"/>
                    <FormRow label="Syllable structures" id="syllableStructures"/>
                    <FormRow label="Stress rule" id="stressRuleName"/>
                    <FormRow label="Phonotactics rule" id="phonotacticsRuleName"/>
                    <FormRow label="Pronunciation rule" id="pronunciationRuleName"/>
                    <FormRow label="Orthography rule" id="orthographyRuleName"/>
                </tbody></table>
                <h3>Grammar</h3>
                <h4>Grammatical categories</h4>
                <FormTextArea rows="5" cols="50" id="grammaticalCategories"/>
                <h4>Word classes</h4>
                <FormTextArea rows="5" cols="50" id="wordClasses"/>
            </EtymographForm>
        </EtymographFormView>
        <p/>
        {errorText !== "" && <div className="errorText">{errorText}</div>}
    </>
}
