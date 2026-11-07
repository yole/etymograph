import {Fragment, useState} from "react";
import {useRouter} from "next/router";
import Link from "next/link";
import {deleteParadigm, useAllowEditGraph} from "@/api";
import {ParadigmViewModel} from "@/models";
import ParadigmForm from "@/forms/ParadigmForm";
import RuleLink from "@/components/RuleLink";
import {Urls} from "@/components/Urls";

export default function ParadigmView({paradigm, refresh}: {paradigm: ParadigmViewModel, refresh: () => Promise<void>}) {
    const router = useRouter()
    const graph = router.query.graph as string
    const canEdit = useAllowEditGraph()
    const [editMode, setEditMode] = useState(false)
    const [deleting, setDeleting] = useState(false)
    const [errorText, setErrorText] = useState("")

    async function deleteParadigmClicked() {
        if (!window.confirm("Delete this paradigm?")) return
        setDeleting(true)
        setErrorText("")
        try {
            const response = await deleteParadigm(graph, paradigm.id)
            if (!response.ok) {
                const error = await response.json()
                setErrorText(error.message || "Failed to delete paradigm")
                return
            }
            await refresh()
            await router.replace(Urls.Rules.morpho(graph, paradigm.language), undefined, {scroll: false})
        }
        catch (error) {
            setErrorText(error instanceof Error ? error.message : "Failed to delete paradigm")
        }
        finally {
            setDeleting(false)
        }
    }

    if (editMode) return <ParadigmForm
        updateId={paradigm.id}
        lang={paradigm.language}
        setEditMode={setEditMode}
        submitted={async () => {
            await refresh()
            setEditMode(false)
        }}
        defaultValues={{
            name: paradigm.name,
            pos: paradigm.pos.join(","),
            text: paradigm.editableText,
            preRuleName: paradigm.preRule?.name,
            postRuleName: paradigm.postRule?.name
        }}
    />

    return <>
        <p>POS: {paradigm.pos.join(", ")}</p>
        {paradigm.preRule && <p>Pre rule: <RuleLink rule={paradigm.preRule}/></p>}
        {paradigm.postRule && <p>Post rule: <RuleLink rule={paradigm.postRule}/></p>}
        <table className="tableWithBorders">
            <thead><tr>
                <th/>
                {paradigm.columns.map((column, index) => <th key={index} scope="col">{column.title}</th>)}
            </tr></thead>
            <tbody>
            {paradigm.rowTitles.map((title, rowIndex) => <tr key={rowIndex}>
                <th scope="row">{title}</th>
                {paradigm.columns.map((column, columnIndex) => <td key={columnIndex}>
                    {column.cells[rowIndex].alternativeRuleIds.map((id, alternativeIndex) =>
                        <Fragment key={alternativeIndex}>
                            {alternativeIndex > 0 && <>&nbsp;|&nbsp;</>}
                            {id === null ? column.cells[rowIndex].alternativeRuleNames[alternativeIndex] :
                                <Link href={`/${graph}/rule/${id}`}>
                                    {column.cells[rowIndex].alternativeRuleSummaries[alternativeIndex] ||
                                        column.cells[rowIndex].alternativeRuleNames[alternativeIndex]}
                                </Link>}
                        </Fragment>)}
                </td>)}
            </tr>)}
            </tbody>
        </table>
        {canEdit && <div>
            <button className="uiButton" disabled={deleting} onClick={() => setEditMode(true)}>Edit Paradigm</button>
            {' '}
            <button className="uiButton" disabled={deleting} onClick={deleteParadigmClicked}>Delete Paradigm</button>
        </div>}
        {errorText && <div className="errorText">{errorText}</div>}
    </>
}
