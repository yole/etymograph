import {addCorpusText, updateCorpusText} from "@/api";
import FormRow from "@/components/FormRow";
import EtymographForm from "@/components/EtymographForm";
import FormTextArea from "@/components/FormTextArea";
import {useRouter} from "next/router";

export default function CorpusTextForm(props) {
    const router = useRouter()
    const graph = router.query.graph
    return <EtymographForm
        create={(data) => addCorpusText(graph, props.lang, data)}
        update={(data) => updateCorpusText(graph, props.updateId, data)}
        {...props}
    >
        <table><tbody>
            <FormRow id="title" label="Title"/>
        </tbody></table>
        <FormTextArea rows="10" cols="50" id="text"/>
        <table>
            <tbody>
            <FormRow id="source" label="Source" />
            </tbody>
        </table>
        <h3>Notes</h3>
        <FormTextArea rows="5" cols="50" id="notes"/>
    </EtymographForm>
}
