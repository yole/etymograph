import {FormEvent, useEffect, useState} from "react";
import {Button, Group, Input, Modal, Radio, Stack, Text} from "@mantine/core";
import {useForm} from "@mantine/form";
import {useRouter} from "next/router";
import {createOpenCompound, updateOpenCompound} from "@/api";
import WordSearch from "@/components/WordSearch";
import WordTextView from "@/components/WordTextView";
import SourceInput from "@/components/SourceInput";
import FormRow from "@/components/FormRow";
import FormTextArea from "@/components/FormTextArea";
import {EtymographFormContext} from "@/components/EtymographForm";
import {OpenCompoundViewModel, WordViewModel} from "@/models";

interface OpenCompoundModalProps {
    opened: boolean
    onClose: () => void
    headWord: WordViewModel
    compound?: OpenCompoundViewModel
    onSaved: () => void
}

export default function OpenCompoundModal(props: OpenCompoundModalProps) {
    const router = useRouter()
    const graph = router.query.graph as string
    const [selectedWordId, setSelectedWordId] = useState<number | null>(null)
    const [saving, setSaving] = useState(false)
    const [errorText, setErrorText] = useState("")
    const form = useForm({
        initialValues: {
            position: "prefix", gloss: props.compound?.gloss ?? "", fullGloss: props.compound?.fullGloss ?? "",
            source: props.compound?.sourceEditableText ?? "", notes: props.compound?.notes ?? ""
        }
    })

    useEffect(() => {
        if (props.opened) {
            setSelectedWordId(null)
            setErrorText("")
            form.setValues({
                position: "prefix", gloss: props.compound?.gloss ?? "", fullGloss: props.compound?.fullGloss ?? "",
                source: props.compound?.sourceEditableText ?? "", notes: props.compound?.notes ?? ""
            })
        }
    }, [props.opened, props.headWord.id, props.headWord.language, props.compound])

    async function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault()
        if ((!props.compound && selectedWordId === null) || saving) return
        setSaving(true)
        setErrorText("")
        try {
            const {position, ...data} = form.values
            const prefix = position === "prefix"
            const response = props.compound ? await updateOpenCompound(graph, props.compound.id, {
                componentIds: props.compound.components.map(component => component.id),
                headIndex: props.compound.headIndex,
                ...data
            }) : await createOpenCompound(graph, {
                componentIds: prefix ? [selectedWordId, props.headWord.id] : [props.headWord.id, selectedWordId],
                headIndex: prefix ? 1 : 0,
                ...data
            })
            if (!response.ok) {
                const error = await response.json()
                setErrorText(error.message || "Failed to save open compound")
                return
            }
            props.onSaved()
        }
        catch (error) {
            setErrorText(error instanceof Error ? error.message : "Failed to save open compound")
        }
        finally {
            setSaving(false)
        }
    }

    return <Modal opened={props.opened} onClose={props.onClose}
                  title={props.compound ? "Edit open compound" : "Add open compound"} size="xl">
        <form onSubmit={submit}>
            <Stack gap="sm">
                <Text size="sm">{props.compound ? props.compound.components.map((component, index) =>
                    <span key={`${component.id}-${index}`}>
                        {index > 0 && " "}
                        <WordTextView text={component.text} syllabograms={component.syllabogramSequence}/>
                    </span>
                ) : <>Head word: <WordTextView text={props.headWord.text}
                                              syllabograms={props.headWord.syllabogramSequence}/></>}</Text>
                {!props.compound && <>
                    <Radio.Group aria-label="Prefix/suffix" {...form.getInputProps("position")}>
                        <Group>
                            <Radio value="prefix" label="Prefix"/>
                            <Radio value="suffix" label="Suffix"/>
                        </Group>
                    </Radio.Group>
                    <WordSearch
                        key={props.headWord.id}
                        enabled={props.opened}
                        language={props.headWord.language}
                        languageReadOnly={true}
                        onLanguageChange={() => {}}
                        selectedWordId={selectedWordId}
                        onSelectWord={setSelectedWordId}
                        excludeWordId={props.headWord.id}
                        showSyllabographic={props.headWord.syllabographic}
                    />
                </>}
                <EtymographFormContext.Provider value={form}>
                    <table><tbody>
                        <FormRow id="gloss" label="Gloss"/>
                        <FormRow id="fullGloss" label="Full gloss"/>
                        <SourceInput id="source" label="Source"/>
                        <tr>
                            <td><Input.Label htmlFor="notes">Notes:</Input.Label></td>
                            <td><FormTextArea rows={3} cols={50} id="notes"/></td>
                        </tr>
                    </tbody></table>
                </EtymographFormContext.Provider>
                {errorText !== "" && <div className="errorText" role="alert">{errorText}</div>}
                <Group>
                    <Button type="submit" disabled={!props.compound && selectedWordId === null} loading={saving}>
                        {props.compound ? "Save" : "Create open compound"}
                    </Button>
                    <Button type="button" variant="default" onClick={props.onClose} disabled={saving}>Cancel</Button>
                </Group>
            </Stack>
        </form>
    </Modal>
}
