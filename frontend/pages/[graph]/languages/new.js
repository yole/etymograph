import {useContext} from "react";
import {addLanguage} from "@/api";
import Breadcrumbs from "@/components/Breadcrumbs";
import EtymographForm from "@/components/EtymographForm";
import FormCheckbox from "@/components/FormCheckbox";
import FormRow from "@/components/FormRow";
import {GraphContext} from "@/components/Contexts";

export default function NewLanguage() {
    const graph = useContext(GraphContext)

    return <>
        <Breadcrumbs title="New Language"/>

        <EtymographForm
            create={(data) => addLanguage(graph, data.name, data.shortName, data.reconstructed)}
            redirectOnCreate={(r) => `/${graph}/language/${r.shortName}`}
        >
            <table><tbody>
                <FormRow label="Name" id="name"/>
                <FormRow label="Short name" id="shortName"/>
            </tbody></table>
            <FormCheckbox label="Reconstructed" id="reconstructed"/>
        </EtymographForm>
    </>
}
