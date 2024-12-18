import {allowEdit, fetchAllLanguagePaths, fetchBackend, updateWordParadigm} from "@/api";
import {useRouter} from "next/router";
import {useContext, useState} from "react";
import WordLink from "@/components/WordLink";
import Breadcrumbs from "@/components/Breadcrumbs";
import {GraphContext} from "@/components/Contexts";
import Link from "next/link";
import {WordParadigmListModel, WordParadigmModel} from "@/models";

export const config = {
    unstable_runtimeJS: true
}

export async function getStaticProps(context){
    return fetchBackend(context.params.graph, `word/${context.params.id}/paradigms`, true)
}

export async function getStaticPaths() {
    const langPaths = await fetchAllLanguagePaths()
    const paths = []
    for (const p of langPaths.paths) {
        let url = `dictionary/${p.params.lang}/all`
        const dictData = await fetchBackend(p.params.graph, url)
        for (const word of dictData.props.loaderData.words) {
            paths.push({params: {graph: p.params.graph, lang: p.params.lang, id: word.id.toString()}})
        }
    }
    return {paths, fallback: allowEdit()}
}

function WordParadigmCell(params) {
    const graph = useContext(GraphContext)
    const alt = params.alt
    if (params.editMode && alt.ruleId) {
        return <input type="text" size={10}
                      value={params.editedParadigm.get(alt.ruleId) ?? alt.word.text}
                      onChange={(e) => {
                          if (params.editedParadigm.has(alt.ruleId) || e.target.value !== alt.word.text) {
                              const newParadigm = new Map(params.editedParadigm)
                              newParadigm.set(alt.ruleId, e.target.value)
                              params.setEditedParadigm(newParadigm)
                          }
                      }}
        />
    }
    return <>
        {alt?.word?.id > 0 ? <WordLink word={alt.word}></WordLink> : alt?.word?.text}
        {alt.ruleId && <span className="paradigmRuleLink">  (<Link href={`/${graph}/rule/${alt.ruleId}`}>rule</Link>)</span>}
    </>
}

function WordParadigm(params) {
    const [editMode, setEditMode] = useState(false)
    const [editedParadigm, setEditedParadigm] = useState(new Map())
    const router = useRouter()

    function saveParadigm() {
        updateWordParadigm(router.query.graph as string, params.wordId, editedParadigm).then(r => {
            setEditMode(false)
            router.replace(router.asPath)
        })
    }

    function cancelEditing() {
        setEditMode(false)
        setEditedParadigm(new Map())
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
                        <WordParadigmCell lang={router.query.lang} alt={alt} editMode={editMode}
                                          editedParadigm={editedParadigm} setEditedParadigm={setEditedParadigm}/>
                    </>)}
                </td>)}
            </tr>)}
            </tbody>
        </table>
        {!editMode && <button onClick={() => setEditMode(true)}>Edit</button>}
        {editMode && <>
            <button onClick={saveParadigm}>Save</button>
            <button onClick={cancelEditing}>Cancel</button>
        </>}
    </>
}

export default function WordParadigms(params) {
    const paradigmList = params.loaderData as WordParadigmListModel
    const graph = useContext(GraphContext)

    return <>
        <Breadcrumbs langId={paradigmList.language} langName={paradigmList.languageFullName}
                     steps={[
                         {url: `/${graph}/dictionary/${paradigmList.language}`, title: "Dictionary"},
                         {url: `/${graph}/word/${paradigmList.language}/${paradigmList.word}`, title: paradigmList.word}
                     ]}
                     title="Paradigms"/>
        {paradigmList.paradigms.map(p => <>
            <WordParadigm wordId={paradigmList.wordId} paradigm={p}/>
        </>)}
    </>
}
