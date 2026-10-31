import {FormEvent, useState} from "react";
import {Button, Modal, Stack, TextInput} from "@mantine/core";
import {useRouter} from "next/router";
import {importTLHDig} from "@/api";
import {TlhDigImportResult} from "@/models";
import WordLink from "@/components/WordLink";

export default function ImportTLHDigModal({onClose}: {onClose: () => void}) {
    const router = useRouter()
    const [file, setFile] = useState<File | null>(null)
    const [importing, setImporting] = useState(false)
    const [errorText, setErrorText] = useState("")
    const [result, setResult] = useState<TlhDigImportResult | null>(null)

    async function submit(event: FormEvent) {
        event.preventDefault()
        if (importing || !file) return
        setImporting(true)
        setErrorText("")
        try {
            const response = await importTLHDig(router.query.graph as string, file)
            if (response.ok()) {
                setResult(await response.result())
                router.replace(router.asPath)
            }
            else {
                setErrorText(await response.error() || "Cannot import TLHDig text")
            }
        }
        catch (error) {
            setErrorText(error instanceof Error ? error.message : "Cannot import TLHDig text")
        }
        finally {
            setImporting(false)
        }
    }

    return <Modal opened={true} onClose={onClose} title={result ? "TLHDig import results" : "Import TLHDig"}
                  size="lg" closeOnClickOutside={!importing} closeOnEscape={!importing} withCloseButton={!importing}>
        {result ? <>
            <h3>Messages</h3>
            {result.messages.length === 0 ? <p>No messages.</p> : <ul>
                {result.messages.map((message, index) => <li key={index}>{message}</li>)}
            </ul>}
            <h3>New words ({result.newWords.length})</h3>
            {result.newWords.length === 0 ? <p>No new words.</p> : <ul>
                {result.newWords.map(word => <li key={word.id}><WordLink word={word} gloss/></li>)}
            </ul>}
            <Button onClick={onClose}>Close</Button>
        </> : <form onSubmit={submit}>
            <Stack>
                <TextInput data-autofocus type="file" label="TLHDig XML file" accept=".xml,application/xml,text/xml"
                           onChange={event => setFile(event.currentTarget.files?.[0] ?? null)}
                           disabled={importing} required/>
                {errorText && <div className="errorText" role="alert">{errorText}</div>}
                <Button type="submit" loading={importing} disabled={!file}>Import</Button>
            </Stack>
        </form>}
    </Modal>
}
