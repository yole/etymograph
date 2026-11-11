import {useState} from "react";
import {useRouter} from "next/router";
import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";
import {faEdit} from "@fortawesome/free-solid-svg-icons";
import {renameWordClassValue, useAllowEditGraph} from "@/api";
import {WordCategoryValueViewModel, WordCategoryViewModel} from "@/models";

function valueText(value: WordCategoryValueViewModel): string {
    return value.name === value.abbreviation ? value.name : `${value.name} (${value.abbreviation})`
}

function WordClassEntry({category, language}: {category: WordCategoryViewModel, language: string}) {
    const router = useRouter()
    const graph = router.query.graph as string
    const canEdit = useAllowEditGraph()
    const [editing, setEditing] = useState<WordCategoryValueViewModel | null>(null)
    const [newValue, setNewValue] = useState("")
    const [saving, setSaving] = useState(false)
    const [error, setError] = useState("")

    async function save() {
        if (saving || !editing) return
        setSaving(true)
        setError("")
        try {
            const response = await renameWordClassValue(graph, language, {
                oldAbbreviation: editing.abbreviation,
                newValue
            })
            if (!response.ok) {
                const result = await response.json()
                setError(result.message || "Failed to rename word class value")
                return
            }
            await router.replace(router.asPath)
            setEditing(null)
        }
        catch (error) {
            setError(error instanceof Error ? error.message : "Failed to rename word class value")
        }
        finally {
            setSaving(false)
        }
    }

    return <li>
        {category.name} ({category.pos.join(", ")}):{' '}
        {category.values.map((value, index) => <span key={value.abbreviation}>
            {index > 0 && ", "}
            {valueText(value)}
            {canEdit && <>{' '}<button type="button" className="inlineButton hoverColorIcon"
                aria-label={`Edit ${valueText(value)}`} title="Edit" disabled={saving}
                onClick={() => {
                    setEditing(value)
                    setNewValue(valueText(value))
                    setError("")
                }}>
                <FontAwesomeIcon icon={faEdit}/>
            </button></>}
        </span>)}
        {canEdit && editing && <form className="wordClassRenameForm" onSubmit={event => {
            event.preventDefault()
            void save()
        }}>
            <label>Class name: <input type="text" value={newValue} autoFocus disabled={saving}
                onChange={event => setNewValue(event.target.value)}/></label>
            <button type="submit" className="uiButtonSubmit" disabled={saving || !newValue.trim()}>Save</button>
            <button type="button" className="uiButton" disabled={saving} onClick={() => setEditing(null)}>Cancel</button>
            {error && <div className="errorText" role="alert">{error}</div>}
        </form>}
    </li>
}

export default function WordClassList({categories, language}: {categories: WordCategoryViewModel[], language: string}) {
    return <ul>
        {categories.map(category => <WordClassEntry key={category.name} category={category} language={language}/>)}
    </ul>
}
