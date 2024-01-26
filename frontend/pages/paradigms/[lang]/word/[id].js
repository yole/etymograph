import {allowEdit, fetchBackend, updateParadigm, updateWordParadigm} from "@/api";
import Link from "next/link";
import {useRouter} from "next/router";
import {useEffect, useState} from "react";

export const config = {
    unstable_runtimeJS: true
}

export async function getStaticProps(context){
    return fetchBackend(`word/${context.params.id}/paradigms`, { headers: { 'Accept': 'application/json'} })
}

export async function getStaticPaths() {
    const {props} = await fetchBackend(`language`)
    const paths = []
    for (const lang of props.loaderData) {
        let url = `dictionary/${lang.shortName}/all`
        const dictData = await fetchBackend(url)
        for (const word of dictData.props.loaderData.words) {
            paths.push({params: {lang: lang.shortName, id: word.id.toString()}})
        }
    }
    return {paths, fallback: allowEdit()}
}

function WordParadigmCell(params) {
    const alt = params.alt
    if (params.editMode && alt.ruleId) {
        return <input type="text" size="10"
                      value={params.editedParadigm.get(alt.ruleId) ?? alt.word}
                      onChange={(e) => {
                          if (params.editedParadigm.has(alt.ruleId) || e.target.value !== alt.word) {
                              const newParadigm = new Map(params.editedParadigm)
                              newParadigm.set(alt.ruleId, e.target.value)
                              params.setEditedParadigm(newParadigm)
                          }
                      }}
        />
    }
    return alt?.wordId > 0 ?
        <Link href={`/word/${params.lang}/${alt?.word}`}>{alt?.word}</Link> : alt?.word
}

function WordParadigm(params) {
    const [editMode, setEditMode] = useState(false)
    const [editedParadigm, setEditedParadigm] = useState(new Map())

    function saveParadigm() {
        updateWordParadigm(params.wordId, editedParadigm).then(r => {
            setEditMode(false)
            params.router.replace(params.router.asPath)
        })
    }

    const p = params.paradigm
    return <>
        <h3>{p.name}</h3>
        <table>
            <thead>
            <tr>
                <td/>
                {p.columnTitles.map(t => <td>{t}</td>)}
            </tr>
            </thead>
            <tbody>
            {p.rowTitles.map((t, i) => <tr>
                <td>{t}</td>
                {p.cells.map(columnWords => <td>
                    {columnWords[i]?.map((alt, ai) => <>
                        {ai > 0 && <>&nbsp;|&nbsp;</>}
                        <WordParadigmCell lang={params.router.query.lang} alt={alt} editMode={editMode}
                                          editedParadigm={editedParadigm} setEditedParadigm={setEditedParadigm}/>
                    </>)}
                </td>)}
            </tr>)}
            </tbody>
        </table>
        {!editMode && <button onClick={() => setEditMode(true)}>Edit</button>}
        {editMode && <>
            <button onClick={saveParadigm}>Save</button>
            <button onClick={() => setEditMode(false)}>Cancel</button>
        </>}
    </>
}

export default function WordParadigms(params) {
    const paradigmList = params.loaderData
    const router = useRouter()

    useEffect(() => {
        document.title = "Etymograph : " + paradigmList.word + " : Paradigms"
    })

    return <>
        <h2><small>
            <Link href={`/`}>Etymograph</Link> {'> '}
            <Link href={`/language/${paradigmList.language}`}>{paradigmList.languageFullName}</Link> {'> '}
            <Link href={`/dictionary/${paradigmList.language}`}>Dictionary</Link> {'> '}
            <Link
                href={`/word/${paradigmList.language}/${paradigmList.word}`}>{paradigmList.word}</Link></small> {'>'} Paradigms
        </h2>
        {paradigmList.paradigms.map(p => <>
            <WordParadigm router={router} wordId={paradigmList.wordId} paradigm={p}/>
        </>)}
    </>
}
