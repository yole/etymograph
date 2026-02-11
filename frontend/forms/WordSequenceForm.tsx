import EtymographForm from "@/components/EtymographForm";
import FormRow from "@/components/FormRow";
import {addWordSequence} from "@/api";
import {useContext} from "react";
import {GraphContext} from "@/components/Contexts";

export default function WordSequenceForm(props) {
    const graph = useContext(GraphContext)

    function createExample(data) {
        return addWordSequence(graph, data.exampleText, data.exampleSource)
    }

    return <EtymographForm create={createExample} {...props}>
        <table><tbody>
            <FormRow id="exampleText" label="Example" size={50} inputAssist={true}/>
            <FormRow id="exampleSource" label="Source"/>
        </tbody></table>
    </EtymographForm>
}
